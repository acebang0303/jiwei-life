<template>
  <view class="publish-page">
    <input class="title-input" v-model="form.title" placeholder="请输入标题..." maxlength="50" />
    <textarea class="content-input" v-model="form.content" placeholder="分享你的美食体验..." maxlength="2000" />
    <view class="image-row">
      <view class="img-item" v-for="(img, i) in images" :key="i">
        <image :src="img" mode="aspectFill" class="img" />
        <text class="img-del" @click="removeImg(i)">✕</text>
      </view>
      <view class="add-img" @click="chooseImg" v-if="images.length < 9">
        <text class="add-icon">+</text>
        <text class="add-text">添加图片</text>
      </view>
    </view>
    <button class="submit-btn" @click="publish">发布笔记</button>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { publishNote } from '@/api/index.js'

const form = ref({ title: '', content: '' })
const images = ref([])

const chooseImg = () => {
  uni.chooseImage({
    count: 9 - images.value.length,
    success: (res) => {
      images.value.push(...res.tempFilePaths)
    }
  })
}

const removeImg = (i) => images.value.splice(i, 1)

const publish = async () => {
  if (!form.value.title.trim() || !form.value.content.trim()) {
    uni.showToast({ title: '请填写标题和内容', icon: 'none' })
    return
  }
  uni.showLoading({ title: '发布中...' })
  try {
    await publishNote({
      title: form.value.title,
      content: form.value.content,
      images: JSON.stringify(images.value)
    })
    uni.hideLoading()
    uni.showToast({ title: '发布成功！', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 1500)
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: e.message || '发布失败', icon: 'none' })
  }
}
</script>

<style lang="scss" scoped>
.publish-page { min-height: 100vh; background: #fff; padding: 24rpx; }
.title-input { font-size: 36rpx; font-weight: bold; padding: 16rpx 0; border-bottom: 1rpx solid #f0f0f0; margin-bottom: 16rpx; width: 100%; }
.content-input { width: 100%; height: 400rpx; font-size: 28rpx; line-height: 1.8; }
.image-row { display: flex; flex-wrap: wrap; gap: 16rpx; margin: 24rpx 0; }
.img-item { position: relative; }
.img { width: 200rpx; height: 200rpx; border-radius: 8rpx; }
.img-del { position: absolute; top: -12rpx; right: -12rpx; width: 40rpx; height: 40rpx; background: $error-color; color: #fff; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 24rpx; }
.add-img { width: 200rpx; height: 200rpx; border: 2rpx dashed #ddd; border-radius: 8rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; }
.add-icon { font-size: 48rpx; color: #ccc; }
.add-text { font-size: 22rpx; color: #ccc; margin-top: 4rpx; }
.submit-btn { width: 100%; height: 88rpx; line-height: 88rpx; background: $primary-color; color: #fff; border-radius: 44rpx; font-size: 30rpx; margin-top: 40rpx; border: none; }
</style>
