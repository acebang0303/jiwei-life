<template>
  <view class="note-detail">
    <view class="header">
      <image class="avatar" :src="note.avatar || defaultAvatar" mode="aspectFill" />
      <view class="author-info">
        <text class="name">{{ note.nickname || '用户' }}</text>
        <text class="time">{{ formatTime(note.createTime) }}</text>
      </view>
      <button class="follow-btn" :class="{ followed: note.isFollowing }"
              @click="follow">{{ note.isFollowing ? '已关注' : '关注' }}</button>
    </view>

    <!-- 关联店铺 -->
    <view class="shop-link" v-if="note.merchantId" @click="goShop">
      <text>🏪 关联店铺：{{ shopName || '查看店铺' }}</text>
      <text class="shop-go">去看看 →</text>
    </view>

    <view class="content">
      <text class="title">{{ note.title }}</text>
      <text class="text">{{ note.content }}</text>
      <view class="note-tags" v-if="note.topicTags">
        <text class="tag" v-for="(t, i) in parseTags(note.topicTags)" :key="i"
              @click="goTagSearch(t)">#{{ t }}</text>
      </view>
    </view>

    <view class="images" v-if="note.images">
      <image v-for="(img, i) in images" :key="i" :src="img" mode="widthFix" class="img" />
    </view>

    <view class="actions">
      <view class="action" @click="like">
        <text>{{ note.isLiked ? '❤️' : '🤍' }} {{ note.likeCount || 0 }}</text>
      </view>
      <view class="action" @click="collect">
        <text>{{ note.isCollected ? '⭐' : '☆' }} {{ note.collectCount || 0 }}</text>
      </view>
      <view class="action">
        <text>💬 {{ (note.comments || []).length }}</text>
      </view>
    </view>

    <view class="comment-section">
      <text class="section-title">评论 ({{ (note.comments || []).length }})</text>
      <view class="comment" v-for="c in (note.comments || [])" :key="c.id">
        <text class="comment-user">{{ c.nickname || '用户' }}：</text>
        <text class="comment-text">{{ c.content }}</text>
        <text class="comment-time">{{ formatTime(c.createTime) }}</text>
        <view class="comment-reply" v-for="r in (c.children || [])" :key="r.id">
          <text class="comment-user reply-user">{{ r.nickname || '用户' }}：</text>
          <text class="comment-text">{{ r.content }}</text>
        </view>
      </view>
      <view class="empty-comment" v-if="(note.comments || []).length === 0">暂无评论，来抢沙发~</view>
    </view>

    <view class="comment-input">
      <input v-model="commentText" placeholder="写评论..." confirm-type="send" @confirm="sendComment" />
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getNoteDetail, likeNote, collectNote, followUser, addComment, reportBehavior } from '@/api/index.js'
import { formatTime } from '@/utils/index.js'

const defaultAvatar = '/static/icons/profile.png'
const note = ref({})
const commentText = ref('')
const shopName = ref('')

const images = computed(() => {
  try { return typeof note.value.images === 'string' ? JSON.parse(note.value.images) : (note.value.images || []) } catch { return [] }
})

const parseTags = (json) => {
  try { return typeof json === 'string' ? JSON.parse(json) : (json || []) } catch { return [] }
}

// 标签点击 → 笔记关联了店铺则直接跳店铺，否则跳搜索页带入标签词
const goTagSearch = (tag) => {
  if (note.value && note.value.merchantId) {
    uni.navigateTo({ url: '/pages/shop/shop?id=' + note.value.merchantId })
  } else {
    uni.navigateTo({ url: '/pages/search/search?keyword=' + encodeURIComponent(tag) })
  }
}

onLoad((options) => {
  const id = options && options.id ? Number(options.id) : null
  if (id) {
    loadDetail(id)
  }
})

const loadDetail = async (id) => {
  try {
    const res = await getNoteDetail(id)
    note.value = res || {}
    reportBehavior('VIEW_NOTE', note.value.id).catch(() => {})
    if (note.value.merchantId) {
      const { getMerchantDetail } = await import('@/api/index.js')
      const shop = await getMerchantDetail(note.value.merchantId)
      shopName.value = shop?.name || ''
    }
  } catch (e) { console.log(e) }
}

const like = async () => {
  try {
    const res = await likeNote(note.value.id)
    note.value.isLiked = res?.liked
    note.value.likeCount = Math.max(0, (note.value.likeCount || 0) + (note.value.isLiked ? 1 : -1))
    if (note.value.isLiked) reportBehavior('LIKE_NOTE', note.value.id).catch(() => {})
  } catch (e) { uni.showToast({ title: '请先登录', icon: 'none' }) }
}

const collect = async () => {
  try {
    const res = await collectNote(note.value.id)
    note.value.isCollected = res?.collected
    note.value.collectCount = Math.max(0, (note.value.collectCount || 0) + (note.value.isCollected ? 1 : -1))
    if (note.value.isCollected) reportBehavior('COLLECT_NOTE', note.value.id).catch(() => {})
  } catch (e) { uni.showToast({ title: '请先登录', icon: 'none' }) }
}

const follow = async () => {
  if (!note.value.userId) return
  try {
    const res = await followUser(note.value.userId)
    note.value.isFollowing = res?.following
    uni.showToast({ title: note.value.isFollowing ? '关注成功' : '已取消关注', icon: 'none' })
  } catch (e) { uni.showToast({ title: '请先登录', icon: 'none' }) }
}

const sendComment = async () => {
  const text = commentText.value.trim()
  if (!text) return
  try {
    await addComment({ noteId: note.value.id, content: text })
    commentText.value = ''
    uni.showToast({ title: '评论成功', icon: 'success' })
    reportBehavior('COMMENT_NOTE', note.value.id).catch(() => {})
    // 重新加载详情刷新评论
    const res = await getNoteDetail(note.value.id)
    if (res) note.value = res
  } catch (e) { uni.showToast({ title: '请先登录', icon: 'none' }) }
}

const goShop = () => uni.navigateTo({ url: '/pages/shop/shop?id=' + note.value.merchantId })
</script>

<style lang="scss" scoped>
.note-detail { min-height: 100vh; background: #fff; padding: 24rpx; padding-bottom: 140rpx; }
.header { display: flex; align-items: center; margin-bottom: 24rpx; }
.avatar { width: 80rpx; height: 80rpx; border-radius: 50%; margin-right: 16rpx; background: #f0f0f0; }
.author-info { flex: 1; }
.name { font-size: 30rpx; font-weight: 600; display: block; }
.time { font-size: 22rpx; color: #999; }
.follow-btn { height: 52rpx; line-height: 52rpx; padding: 0 20rpx; background: $primary-color; color: #fff; border-radius: 26rpx; font-size: 22rpx; border: none; }
.follow-btn.followed { background: #e8e8e8; color: #999; }
.shop-link {
  display: flex; justify-content: space-between; align-items: center;
  background: #FFF7F3; border-radius: 12rpx; padding: 16rpx 20rpx;
  margin-bottom: 20rpx; font-size: 26rpx; color: $primary-color;
}
.shop-go { font-size: 24rpx; }
.content { margin-bottom: 24rpx; }
.title { font-size: 36rpx; font-weight: bold; display: block; margin-bottom: 16rpx; }
.text { font-size: 30rpx; line-height: 1.8; }
.note-tags { display: flex; gap: 12rpx; margin-top: 16rpx; flex-wrap: wrap; }
.tag { font-size: 22rpx; color: $primary-color; }
.images { margin-bottom: 24rpx; }
.img { width: 100%; border-radius: 12rpx; margin-bottom: 8rpx; }
.actions { display: flex; gap: 32rpx; padding: 16rpx 0; border-top: 1rpx solid #f0f0f0; border-bottom: 1rpx solid #f0f0f0; margin-bottom: 24rpx; font-size: 26rpx; }
.section-title { font-size: 30rpx; font-weight: bold; display: block; margin-bottom: 16rpx; }
.comment { padding: 16rpx 0; border-bottom: 1rpx solid #f5f5f5; }
.comment-reply { margin-top: 10rpx; padding: 10rpx 16rpx; background: #faf7f3; border-radius: 8rpx; }
.reply-user { color: $warning-color; }
.comment-user { color: $primary-color; font-size: 26rpx; }
.comment-text { font-size: 28rpx; }
.comment-time { font-size: 22rpx; color: #ccc; display: block; margin-top: 4rpx; }
.empty-comment { text-align: center; padding: 40rpx 0; color: #999; font-size: 26rpx; }
.comment-input { position: fixed; bottom: 0; left: 0; right: 0; padding: 16rpx 24rpx; background: #fff; border-top: 1rpx solid #f0f0f0; padding-bottom: calc(16rpx + env(safe-area-inset-bottom)); }
.comment-input input { height: 72rpx; background: #f5f5f5; border-radius: 36rpx; padding: 0 24rpx; font-size: 26rpx; }
</style>
