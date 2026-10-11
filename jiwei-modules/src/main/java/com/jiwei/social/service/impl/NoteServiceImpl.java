package com.jiwei.social.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jiwei.auth.mapper.UserMapper;
import com.jiwei.common.context.BaseContext;
import com.jiwei.common.exception.BaseException;
import com.jiwei.common.result.PageResult;
import com.jiwei.pojo.entity.Comment;
import com.jiwei.pojo.entity.Note;
import com.jiwei.pojo.entity.User;
import com.jiwei.social.like.NoteLikeService;
import com.jiwei.social.mapper.NoteMapper;
import com.jiwei.social.mq.NoteEmbeddingProducer;
import com.jiwei.social.service.CommentService;
import com.jiwei.social.service.FollowService;
import com.jiwei.social.service.NoteService;
import com.jiwei.user.service.GrowthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 笔记服务实现类
 *
 * Feed流策略:
 * - 大V(粉丝>10000): 只写自己时间线，粉丝拉模式实时拉取
 * - 普通用户: 写粉丝Feed收件箱(Redis Sorted Set, 上限500条)
 *
 * 热门排行榜:
 * - Redis ZSet: hot_notes:weekly
 * - Score = likeCount*2 + commentCount*3 + collectCount*5
 */
@Service
@DS("social")
@Slf4j
public class NoteServiceImpl implements NoteService {

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private NoteEmbeddingProducer noteEmbeddingProducer;

    @Autowired
    private NoteLikeService noteLikeService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private FollowService followService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private GrowthService growthService;

    /** 发布一篇笔记获得的成长值 */
    private static final int NOTE_GROWTH = 5;

    private static final String HOT_NOTES_KEY = "hot_notes:weekly";
    private static final int FEED_MAX_SIZE = 500;

    @Override
    @Transactional
    public void publish(Note note) {
        //1、设置作者ID
        note.setUserId(BaseContext.getCurrentId());
        //开发环境直接过审，否则内容永远待审核不展示；生产环境应接 AI AuditAgent 审核
        note.setAuditStatus(1);

        //2、保存笔记
        noteMapper.insert(note);
        log.info("笔记发布成功，noteId: {}, userId: {}", note.getId(), note.getUserId());

        //2.1、发放成长值（发布笔记行为）
        growthService.addGrowth(note.getUserId(), NOTE_GROWTH);

        //发消息同步笔记向量（消费端按 audit_status 决定是否重建）
        noteEmbeddingProducer.send(note.getId());

        //3、异步触发AI内容审核
        //TODO: RocketMQ消息 → AI Audit Agent → 更新audit_status

        //4、笔记写入Feed流
        // 普通用户的粉丝Feed收件箱由消费者异步写入
        // TODO: 判断粉丝数量，决定推模式还是拉模式
    }

    @Override
    public PageResult feed(int page, int pageSize) {
        Long userId = BaseContext.getCurrentId();

        //1、拉模式: 从关注的大V获取最新笔记
        //TODO: 查询关注列表 → 拉取大V的最新笔记

        //2、推模式: 从Redis收件箱获取
        String feedKey = "feed:" + userId;
        Set<ZSetOperations.TypedTuple<Object>> feedItems = redisTemplate.opsForZSet()
                .reverseRangeWithScores(feedKey, 0, pageSize - 1);

        //3、合并排序 → 分页返回
        if (feedItems != null && !feedItems.isEmpty()) {
            List<Long> ids = feedItems.stream()
                    .map(t -> Long.valueOf(t.getValue().toString()))
                    .collect(Collectors.toList());
            List<Note> notes = queryNotesOrdered(ids);
            fillUserInfo(notes);
            Long total = redisTemplate.opsForZSet().size(feedKey);
            return new PageResult(total != null ? total : notes.size(), notes);
        }

        // 降级: 全站笔记流（已审核通过，按时间倒序）
        Page<Note> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Note> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Note::getAuditStatus, 1) // 已审核通过
               .orderByDesc(Note::getCreateTime);
        Page<Note> result = noteMapper.selectPage(p, wrapper);
        fillUserInfo(result.getRecords());
        return new PageResult(result.getTotal(), result.getRecords());
    }

    @Override
    public PageResult hotRanking(int page, int pageSize) {
        //1、从Redis ZSet获取热门排行（按score倒序）
        Set<ZSetOperations.TypedTuple<Object>> hotNotes = redisTemplate.opsForZSet()
                .reverseRangeWithScores(HOT_NOTES_KEY,
                        (long) (page - 1) * pageSize,
                        (long) page * pageSize - 1);
        Long total = redisTemplate.opsForZSet().size(HOT_NOTES_KEY);

        //2、ZSet为空（未初始化热度分）→ 降级按热度分查全站
        if (hotNotes == null || hotNotes.isEmpty() || total == null || total == 0) {
            Page<Note> p = new Page<>(page, pageSize);
            LambdaQueryWrapper<Note> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Note::getAuditStatus, 1)
                   .last("ORDER BY (like_count * 2 + comment_count * 3 + collect_count * 5) DESC, create_time DESC");
            Page<Note> result = noteMapper.selectPage(p, wrapper);
            fillUserInfo(result.getRecords());
            return new PageResult(result.getTotal(), result.getRecords());
        }

        //3、按ZSet顺序批量查询笔记详情
        List<Long> ids = hotNotes.stream()
                .map(t -> Long.valueOf(t.getValue().toString()))
                .collect(Collectors.toList());
        List<Note> notes = queryNotesOrdered(ids);
        fillUserInfo(notes);
        return new PageResult(total, notes);
    }

    @Override
    public boolean like(Long noteId) {
        Long userId = BaseContext.getCurrentId();

        //1、笔记存在校验
        Note note = noteMapper.selectById(noteId);
        if (note == null) {
            return false;
        }

        //2、走 Redis 挡写 → 攒批 → 热点聚合链路（异步落库，最终一致）
        NoteLikeService.ToggleResult result = noteLikeService.toggle(noteId, userId);

        //3、用实时点赞数更新热度分
        note.setLikeCount((int) result.count());
        updateHotScore(noteId, note);

        return result.liked();
    }

    @Override
    @Transactional
    public boolean collect(Long noteId) {
        Long userId = BaseContext.getCurrentId();
        String collectKey = "note:collect:" + noteId;
        String userCollectKey = "user:collect:" + userId;

        //1、Redis Set存储收藏关系
        Boolean isCollected = redisTemplate.opsForSet().isMember(collectKey, userId.toString());
        Note note = noteMapper.selectById(noteId);
        if (note == null) {
            return false;
        }

        if (Boolean.TRUE.equals(isCollected)) {
            //2、取消收藏
            redisTemplate.opsForSet().remove(collectKey, userId.toString());
            redisTemplate.opsForSet().remove(userCollectKey, noteId.toString());
            note.setCollectCount(Math.max(0, note.getCollectCount() - 1));
            noteMapper.updateById(note);
        } else {
            //3、收藏
            redisTemplate.opsForSet().add(collectKey, userId.toString());
            redisTemplate.opsForSet().add(userCollectKey, noteId.toString());
            note.setCollectCount((note.getCollectCount() != null ? note.getCollectCount() : 0) + 1);
            noteMapper.updateById(note);
        }
        updateHotScore(noteId, note);
        return !isCollected;
    }

    @Override
    public Note detail(Long id) {
        Note note = noteMapper.selectById(id);
        if (note == null) {
            throw new BaseException("笔记不存在或已删除");
        }
        //1、浏览量 +1
        note.setViewCount((note.getViewCount() != null ? note.getViewCount() : 0) + 1);
        noteMapper.updateById(note);
        updateHotScore(id, note);

        //2、作者信息 + 当前用户互动状态
        fillUserInfo(Collections.singletonList(note));

        //3、评论列表（含二级回复树，复用 CommentService 的树形查询）
        PageResult commentPage = commentService.pageByNoteId(id, 1, 100);
        List<Comment> comments = (List<Comment>) commentPage.getRecords();
        note.setComments(comments);
        return note;
    }

    @Override
    public PageResult myNotes(int page, int pageSize) {
        Long userId = BaseContext.getCurrentId();
        Page<Note> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Note> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Note::getUserId, userId)
               .orderByDesc(Note::getCreateTime);
        Page<Note> result = noteMapper.selectPage(p, wrapper);
        fillUserInfo(result.getRecords());
        return new PageResult(result.getTotal(), result.getRecords());
    }

    @Override
    public PageResult pageAudit(int page, int pageSize, Integer auditStatus) {
        Page<Note> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Note> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(auditStatus != null, Note::getAuditStatus, auditStatus)
               .orderByDesc(Note::getCreateTime);
        Page<Note> result = noteMapper.selectPage(p, wrapper);
        fillUserInfo(result.getRecords());
        return new PageResult(result.getTotal(), result.getRecords());
    }

    @Override
    public void audit(Long id, Integer auditStatus, String auditReason) {
        Note note = noteMapper.selectById(id);
        if (note == null) {
            throw new BaseException("笔记不存在");
        }
        note.setAuditStatus(auditStatus);
        noteMapper.updateById(note);
        log.info("笔记审核完成: id={}, status={}, reason={}", id, auditStatus, auditReason);
    }

    /**
     * 按ID列表查询并保持传入顺序
     */
    private List<Note> queryNotesOrdered(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Note> noteMap = noteMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Note::getId, n -> n));
        return ids.stream()
                .map(noteMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 填充作者昵称/头像 + 当前用户点赞/收藏/关注状态
     * 跨库查 user 表：本方法无事务，dynamic-datasource 可安全路由
     */
    private void fillUserInfo(List<Note> notes) {
        if (notes == null || notes.isEmpty()) {
            return;
        }
        Long currentId = BaseContext.getCurrentId();
        List<Long> userIds = notes.stream()
                .map(Note::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        notes.forEach(n -> {
            User author = userMap.get(n.getUserId());
            if (author != null) {
                n.setNickname(author.getNickname());
                n.setAvatar(author.getAvatar());
            }
            n.setIsLiked(currentId != null && noteLikeService.currentLiked(n.getId(), currentId));
            String collectKey = "note:collect:" + n.getId();
            n.setIsCollected(currentId != null && Boolean.TRUE.equals(
                    redisTemplate.opsForSet().isMember(collectKey, currentId.toString())));
            n.setIsFollowing(currentId != null && followService.isFollowing(currentId, n.getUserId()));
        });
    }

    /**
     * 更新笔记热度分
     * Score = likeCount*2 + commentCount*3 + collectCount*5
     */
    private void updateHotScore(Long noteId, Note note) {
        double score = (note.getLikeCount() != null ? note.getLikeCount() : 0) * 2.0
                + (note.getCommentCount() != null ? note.getCommentCount() : 0) * 3.0
                + (note.getCollectCount() != null ? note.getCollectCount() : 0) * 5.0;
        redisTemplate.opsForZSet().add(HOT_NOTES_KEY, noteId.toString(), score);
    }
}
