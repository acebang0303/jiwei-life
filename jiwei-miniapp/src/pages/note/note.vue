<template>
  <view class="note-page">
    <!-- 顶部Tab -->
    <view class="note-tabs">
      <view class="tab" :class="{ active: feedType === 'feed' }" @click="switchFeed('feed')">推荐</view>
      <view class="tab" :class="{ active: feedType === 'hot' }" @click="switchFeed('hot')">热门</view>
    </view>

    <!-- Feed流 -->
    <view class="note-list">
      <view class="note-card" v-for="note in notes" :key="note.id" @click="goDetail(note.id)">
        <view class="note-header">
          <image class="avatar" :src="note.avatar || defaultAvatar" mode="aspectFill" />
          <view class="author-info">
            <text class="author-name">{{ note.nickname || '用户' }}</text>
            <text class="note-time">{{ formatTime(note.createTime) }}</text>
          </view>
          <button class="follow-btn" :class="{ followed: note.isFollowing }"
                  @click.stop="follow(note)">{{ note.isFollowing ? '已关注' : '关注' }}</button>
        </view>
        <view class="note-content">
          <text class="note-title">{{ note.title }}</text>
          <text class="note-text">{{ note.content }}</text>
          <view class="note-images" v-if="note.images">
            <image v-for="(img, i) in parseImages(note.images)" :key="i"
                   :src="img" mode="aspectFill" class="note-img" />
          </view>
          <view class="note-tags" v-if="note.topicTags">
            <text class="tag" v-for="(t, i) in parseTags(note.topicTags)" :key="i"
                  @click.stop="goTagSearch(t, note)">#{{ t }}</text>
          </view>
        </view>
        <view class="note-footer">
          <view class="footer-action" @click.stop="like(note)">
            <text>{{ note.isLiked ? '❤️' : '🤍' }}</text>
            <text>{{ note.likeCount || 0 }}</text>
          </view>
          <view class="footer-action" @click.stop="collect(note)">
            <text>{{ note.isCollected ? '⭐' : '☆' }}</text>
            <text>{{ note.collectCount || 0 }}</text>
          </view>
          <view class="footer-action">
            <text>💬</text>
            <text>{{ note.commentCount || 0 }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 发布按钮 -->
    <view class="publish-btn" @click="goPublish">
      <text class="publish-icon">✏️</text>
    </view>

    <view class="empty" v-if="notes.length === 0">
      <text>暂无笔记，快来发布第一篇吧~</text>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { onReachBottom } from '@dcloudio/uni-app'
import { getNoteFeed, getNoteHot, likeNote, collectNote, followUser, reportBehavior } from '@/api/index.js'
import { formatTime } from '@/utils/index.js'

const defaultAvatar = '/static/icons/profile.png'
const feedType = ref('feed')
const notes = ref([])
const page = ref(1)
const pageSize = 10
const hasMore = ref(true)

const loadNotes = async (reset) => {
  if (reset) {
    page.value = 1
    hasMore.value = true
  }
  if (!hasMore.value) return
  try {
    const res = feedType.value === 'feed'
      ? await getNoteFeed(page.value, pageSize)
      : await getNoteHot(page.value, pageSize)
    const list = res?.records || []
    notes.value = reset ? list : [...notes.value, ...list]
    hasMore.value = page.value * pageSize < (res?.total || list.length)
    page.value += 1
  } catch (e) { console.log('加载笔记失败', e) }
}

const switchFeed = async (type) => {
  feedType.value = type
  loadNotes(true)
}

// 触底加载更多
onReachBottom(() => {
  loadNotes(false)
})

const like = async (note) => {
  try {
    const result = await likeNote(note.id)
    note.isLiked = result?.liked
    note.likeCount = Math.max(0, (note.likeCount || 0) + (note.isLiked ? 1 : -1))
    if (note.isLiked) reportBehavior('LIKE_NOTE', note.id).catch(() => {})
  } catch (e) { uni.showToast({ title: '请先登录', icon: 'none' }) }
}

const collect = async (note) => {
  try {
    const result = await collectNote(note.id)
    note.isCollected = result?.collected
    note.collectCount = Math.max(0, (note.collectCount || 0) + (note.isCollected ? 1 : -1))
    if (note.isCollected) reportBehavior('COLLECT_NOTE', note.id).catch(() => {})
  } catch (e) { uni.showToast({ title: '请先登录', icon: 'none' }) }
}

const follow = async (note) => {
  if (!note.userId) return
  try {
    const result = await followUser(note.userId)
    note.isFollowing = result?.following
    uni.showToast({ title: note.isFollowing ? '关注成功' : '已取消关注', icon: 'none' })
  } catch (e) { uni.showToast({ title: '请先登录', icon: 'none' }) }
}

const parseImages = (json) => {
  try { return typeof json === 'string' ? JSON.parse(json) : json } catch { return [] }
}

const parseTags = (json) => {
  try { return typeof json === 'string' ? JSON.parse(json) : json } catch { return [] }
}

const goDetail = (id) => uni.navigateTo({ url: '/pages/note/detail?id=' + id })
const goPublish = () => uni.navigateTo({ url: '/pages/note/publish' })

// 标签点击 → 笔记关联了店铺则直接跳店铺，否则跳搜索页带入标签词
const goTagSearch = (tag, note) => {
  if (note && note.merchantId) {
    uni.navigateTo({ url: '/pages/shop/shop?id=' + note.merchantId })
  } else {
    uni.navigateTo({ url: '/pages/search/search?keyword=' + encodeURIComponent(tag) })
  }
}

onMounted(() => switchFeed('feed'))
</script>

<style lang="scss" scoped>
.note-page { min-height: 100vh; background: $bg-color; padding-bottom: 120rpx; }
.note-tabs { display: flex; background: #fff; padding: 0 24rpx; border-bottom: 1rpx solid #f0f0f0; position: sticky; top: 0; z-index: 10; }
.tab { flex: 1; text-align: center; padding: 24rpx 0; font-size: 28rpx; color: #666; }
.tab.active { color: $primary-color; font-weight: 600; }
.note-list { padding: 16rpx 24rpx; }
.note-card { background: #fff; border-radius: 16rpx; padding: 24rpx; margin-bottom: 16rpx; box-shadow: $shadow; }
.note-header { display: flex; align-items: center; margin-bottom: 16rpx; }
.avatar { width: 72rpx; height: 72rpx; border-radius: 50%; margin-right: 12rpx; background: #f0f0f0; }
.author-info { flex: 1; }
.author-name { font-size: 28rpx; font-weight: 600; display: block; }
.note-time { font-size: 22rpx; color: #999; }
.follow-btn {
  height: 52rpx; line-height: 52rpx; padding: 0 20rpx;
  background: $primary-color; color: #fff; border-radius: 26rpx;
  font-size: 22rpx; border: none;
}
.follow-btn.followed { background: #e8e8e8; color: #999; }
.note-content { margin-bottom: 16rpx; }
.note-title { font-size: 32rpx; font-weight: bold; display: block; margin-bottom: 8rpx; }
.note-text { font-size: 28rpx; color: #333; display: block; line-height: 1.6;
  overflow: hidden; text-overflow: ellipsis; display: -webkit-box;
  -webkit-line-clamp: 3; -webkit-box-orient: vertical; }
.note-images { display: flex; gap: 8rpx; margin-top: 12rpx; flex-wrap: wrap; }
.note-img { width: 210rpx; height: 210rpx; border-radius: 8rpx; background: #f0f0f0; }
.note-tags { display: flex; gap: 12rpx; margin-top: 12rpx; flex-wrap: wrap; }
.tag { font-size: 22rpx; color: $primary-color; }
.note-footer { display: flex; gap: 32rpx; border-top: 1rpx solid #f5f5f5; padding-top: 12rpx; }
.footer-action { display: flex; align-items: center; gap: 4rpx; font-size: 24rpx; color: #999; }
.publish-btn {
  position: fixed; bottom: 120rpx; right: 32rpx;
  width: 96rpx; height: 96rpx; border-radius: 50%;
  background: linear-gradient(135deg, $primary-color, $primary-light);
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 4rpx 20rpx rgba(255,107,53,0.4); z-index: 99;
}
.publish-icon { font-size: 40rpx; }
.empty { text-align: center; padding: 160rpx 0; color: #999; font-size: 28rpx; }
</style>
