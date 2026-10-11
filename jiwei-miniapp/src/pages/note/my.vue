<template>
  <view class="my-note-page">
    <view class="note-list">
      <view class="note-card" v-for="note in notes" :key="note.id" @click="goDetail(note.id)">
        <view class="note-content">
          <text class="note-title">{{ note.title }}</text>
          <text class="note-text">{{ note.content }}</text>
          <view class="note-images" v-if="note.images">
            <image v-for="(img, i) in parseImages(note.images)" :key="i"
                   :src="img" mode="aspectFill" class="note-img" />
          </view>
          <view class="note-tags" v-if="note.topicTags">
            <text class="tag" v-for="(t, i) in parseTags(note.topicTags)" :key="i">#{{ t }}</text>
          </view>
        </view>
        <view class="note-footer">
          <text class="footer-item">❤️ {{ note.likeCount || 0 }}</text>
          <text class="footer-item">⭐ {{ note.collectCount || 0 }}</text>
          <text class="footer-item">💬 {{ note.commentCount || 0 }}</text>
          <text class="audit-tag" :style="{ color: note.auditStatus === 1 ? $primaryColor : (note.auditStatus === 2 ? $errorColor : $warningColor) }">
            {{ note.auditStatus === 1 ? '已发布' : (note.auditStatus === 2 ? '审核未通过' : '审核中') }}
          </text>
        </view>
      </view>
    </view>

    <view class="empty" v-if="notes.length === 0">
      <text>还没有发布过笔记</text>
      <text class="empty-hint">分享你的美食体验，让更多人发现好店</text>
      <button class="publish-btn" @click="goPublish">去发布</button>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getMyNotes } from '@/api/index.js'

const notes = ref([])

const parseImages = (json) => {
  try { return typeof json === 'string' ? JSON.parse(json) : json } catch { return [] }
}
const parseTags = (json) => {
  try { return typeof json === 'string' ? JSON.parse(json) : json } catch { return [] }
}

const goDetail = (id) => uni.navigateTo({ url: '/pages/note/detail?id=' + id })
const goPublish = () => uni.navigateTo({ url: '/pages/note/publish' })

onMounted(async () => {
  try {
    const res = await getMyNotes(1, 20)
    notes.value = res?.records || []
  } catch (e) { console.log('加载我的笔记失败', e) }
})
</script>

<style lang="scss" scoped>
.my-note-page { min-height: 100vh; background: $bg-color; padding: 16rpx 24rpx; }
.note-card { background: #fff; border-radius: 16rpx; padding: 24rpx; margin-bottom: 16rpx; box-shadow: $shadow; }
.note-title { font-size: 32rpx; font-weight: bold; display: block; margin-bottom: 8rpx; }
.note-text {
  font-size: 28rpx; color: #333; display: block; line-height: 1.6;
  overflow: hidden; text-overflow: ellipsis; display: -webkit-box;
  -webkit-line-clamp: 3; -webkit-box-orient: vertical;
}
.note-images { display: flex; gap: 8rpx; margin-top: 12rpx; flex-wrap: wrap; }
.note-img { width: 200rpx; height: 200rpx; border-radius: 8rpx; background: #f0f0f0; }
.note-tags { display: flex; gap: 12rpx; margin-top: 12rpx; flex-wrap: wrap; }
.tag { font-size: 22rpx; color: $primary-color; }
.note-footer { display: flex; align-items: center; gap: 24rpx; border-top: 1rpx solid #f5f5f5; padding-top: 12rpx; margin-top: 12rpx; }
.footer-item { font-size: 24rpx; color: #999; }
.audit-tag { margin-left: auto; font-size: 22rpx; color: $warning-color; }
.audit-tag.a1 { color: $primary-color; }
.audit-tag.a2 { color: $error-color; }
.empty { text-align: center; padding: 120rpx 0; color: #999; font-size: 28rpx; }
.empty-hint { font-size: 24rpx; color: #ccc; display: block; margin-top: 8rpx; }
.publish-btn {
  margin-top: 32rpx; width: 240rpx; height: 72rpx; line-height: 72rpx;
  background: $primary-color; color: #fff; border-radius: 36rpx; font-size: 28rpx; border: none;
}
</style>
