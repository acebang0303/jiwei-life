package com.jiwei.social.like;

import com.jiwei.pojo.entity.NoteLike;
import com.jiwei.social.mapper.NoteLikeMapper;
import com.jiwei.social.mapper.NoteMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 点赞落库的 at-least-once 幂等性 + 对账方向 + 内存治理回归。
 */
class NoteLikePersistAndReconcileTest {

    private final NoteLikeMapper noteLikeMapper = mock(NoteLikeMapper.class);
    private final NoteMapper noteMapper = mock(NoteMapper.class);

    /** 构造一个「抢锁必定成功」的闸门，让对账逻辑可在单测里直跑。 */
    private static NoteLikeSyncLock alwaysAcquirableLock() {
        RedissonClient redisson = mock(RedissonClient.class);
        RLock rlock = mock(RLock.class);
        when(redisson.getLock(anyString())).thenReturn(rlock);
        try {
            when(rlock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        when(rlock.isHeldByCurrentThread()).thenReturn(true);
        return new NoteLikeSyncLock(redisson);
    }

    // ---------- NoteLikePersistService：计数增量必须取自实际影响行数 ----------

    @Test
    void shouldApplyDeltaFromAffectedRowsOnFirstDelivery() {
        NoteLikePersistService service = new NoteLikePersistService(noteLikeMapper, noteMapper);
        when(noteLikeMapper.insertIgnoreBatch(any())).thenReturn(1);

        NoteLikePersistService.Result r = service.persist(List.of(
                NoteLikeEvent.of(true, 7L, 100L)));

        assertThat(r.appliedLikes()).isEqualTo(1);
        verify(noteMapper).incrLikeCount(7L, 1);
    }

    @Test
    void shouldNotDoubleCountWhenSameEventsAreReplayed() {
        NoteLikePersistService service = new NoteLikePersistService(noteLikeMapper, noteMapper);
        // 首次投递：唯一键插入成功（影响 1 行）
        when(noteLikeMapper.insertIgnoreBatch(any())).thenReturn(1);
        List<NoteLikeEvent> events = List.of(NoteLikeEvent.of(true, 7L, 100L));
        service.persist(events);
        verify(noteMapper, times(1)).incrLikeCount(7L, 1);

        // 重放（LTRIM 之前进程崩溃）：INSERT IGNORE 影响 0 行 → 计数不得再动
        when(noteLikeMapper.insertIgnoreBatch(any())).thenReturn(0);
        service.persist(events);

        verify(noteMapper, times(1)).incrLikeCount(anyLong(), anyInt());
    }

    @Test
    void shouldCountUnlikeByDeletedRows() {
        NoteLikePersistService service = new NoteLikePersistService(noteLikeMapper, noteMapper);
        when(noteLikeMapper.deleteBatch(any())).thenReturn(1);

        service.persist(List.of(NoteLikeEvent.of(false, 7L, 100L)));

        verify(noteMapper).incrLikeCount(7L, -1);
    }

    @Test
    void shouldKeepOnlyLastIntentPerUserInOneBatch() {
        NoteLikePersistService service = new NoteLikePersistService(noteLikeMapper, noteMapper);
        when(noteLikeMapper.insertIgnoreBatch(any())).thenReturn(1);

        // 同一批里同一用户先赞后取消：只应保留最后的 UNLIKE 意图
        service.persist(List.of(
                NoteLikeEvent.of(true, 7L, 100L),
                NoteLikeEvent.of(false, 7L, 100L)));

        verify(noteLikeMapper, never()).insertIgnoreBatch(any());
        ArgumentCaptor<List<NoteLike>> captor = ArgumentCaptor.forClass(List.class);
        verify(noteLikeMapper).deleteBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }

    // ---------- SyncReconciler：方向必须是「DB 为账本，修 Redis」 ----------

    @Test
    void shouldRepairRedisDriftAgainstDatabaseBaseline() {
        NoteLikeStore store = mock(NoteLikeStore.class);
        NoteLikeEventConsumer consumer = mock(NoteLikeEventConsumer.class);
        SyncReconciler reconciler = new SyncReconciler(store, noteMapper, consumer, alwaysAcquirableLock());

        when(consumer.drainAll()).thenReturn(0);
        // Redis 领先（上一个窗口的事件已加计数但未落库），另有 note 2 完全丢失
        when(store.allCounts()).thenReturn(Map.of(1L, 329L, 3L, 9L));
        when(noteMapper.selectLikeCounts()).thenReturn(List.of(
                Map.of("id", 1L, "likeCount", 328),
                Map.of("id", 2L, "likeCount", 5),
                Map.of("id", 3L, "likeCount", 9)));

        reconciler.reconcile();

        // note 1 被压回 DB 基线，note 2 从 DB 补回 Redis；note 3 一致不动
        verify(store).overwriteCount(1L, 328L);
        verify(store).overwriteCount(2L, 5L);
        verify(store, never()).overwriteCount(eq(3L), anyLong());
    }

    @Test
    void shouldDrainQueueBeforeComparingCounts() {
        NoteLikeStore store = mock(NoteLikeStore.class);
        NoteLikeEventConsumer consumer = mock(NoteLikeEventConsumer.class);
        SyncReconciler reconciler = new SyncReconciler(store, noteMapper, consumer, alwaysAcquirableLock());

        when(consumer.drainAll()).thenReturn(42);
        when(store.allCounts()).thenReturn(Map.of());
        when(noteMapper.selectLikeCounts()).thenReturn(List.of());

        reconciler.reconcile();

        verify(consumer).drainAll();
    }

    // ---------- HotKeyDetector：空闲计数条目必须被回收 ----------

    @Test
    void shouldEvictIdleCountersFromHotKeyDetector() throws Exception {
        LocalHotLikeBuffer buffer = new LocalHotLikeBuffer(mock(NoteLikeStore.class), 1500);
        HotKeyDetector detector = new HotKeyDetector(buffer, 30, 5);

        // 低于阈值的一次点赞：窗口评估后该笔记既非热点也无新增计数 → 条目应被回收
        detector.hit(11L);
        detector.evaluate();
        assertThat(trackedCounters(detector)).isZero();

        // 热点笔记的计数器必须保留（否则降级判定会失效）
        for (int i = 0; i < 30; i++) {
            detector.hit(22L);
        }
        detector.evaluate();
        assertThat(buffer.isHot(22L)).isTrue();
        detector.evaluate();
        assertThat(trackedCounters(detector)).isEqualTo(1);
    }

    private static int trackedCounters(HotKeyDetector detector) throws Exception {
        java.lang.reflect.Field field = HotKeyDetector.class.getDeclaredField("counters");
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(detector)).size();
    }

    // ---------- LocalHotLikeBuffer：退出热点模式不得丢残余增量 ----------

    @Test
    void shouldReplayResidualOpsWhenMarkingCold() {
        NoteLikeStore store = mock(NoteLikeStore.class);
        when(store.flushAggregated(any())).thenReturn(new NoteLikeStore.FlushResult(2, 2));
        LocalHotLikeBuffer buffer = new LocalHotLikeBuffer(store, 1500);
        buffer.markHot(5L);
        buffer.record(5L, 100L, true);
        buffer.record(5L, 101L, true);

        buffer.markCold(5L);

        // 退出热点时残余增量必须补刷，且走的是同一个原子批量 Lua（按实际影响算增量）
        ArgumentCaptor<List<NoteLikeStore.AggregatedOps>> captor = ArgumentCaptor.forClass(List.class);
        verify(store).flushAggregated(captor.capture());
        NoteLikeStore.AggregatedOps ops = captor.getValue().get(0);
        assertThat(ops.noteId()).isEqualTo(5L);
        assertThat(ops.likeUsers()).containsExactlyInAnyOrder(100L, 101L);
        assertThat(ops.unlikeUsers()).isEmpty();
        assertThat(buffer.isHot(5L)).isFalse();
        assertThat(buffer.pendingDelta(5L)).isZero();
    }
}
