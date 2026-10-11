<template>
  <view class="search-page">
    <view class="search-bar">
      <input class="search-input" v-model="keyword" placeholder="AI智能搜索... 如「适合约会的浪漫西餐厅」"
             confirm-type="search" @confirm="search" focus />
      <text class="search-btn" @click="search">搜索</text>
    </view>

    <!-- 热门搜索 -->
    <view class="hot-section" v-if="results.length === 0 && !searched">
      <text class="section-title">🔥 热门搜索</text>
      <view class="hot-tags">
        <text class="tag" v-for="t in hotTags" :key="t" @click="quickSearch(t)">{{ t }}</text>
      </view>
    </view>

    <!-- 历史搜索 -->
    <view class="history-section" v-if="history.length > 0 && results.length === 0 && !searched">
      <text class="section-title">🕐 最近搜索</text>
      <view class="history-tags">
        <text class="tag" v-for="h in history" :key="h" @click="quickSearch(h)">{{ h }}</text>
      </view>
    </view>

    <!-- 搜索结果 -->
    <view class="results" v-if="results.length > 0">
      <text class="result-count">为你找到 {{ results.length }} 个结果</text>
      <view class="result-item" v-for="r in results" :key="r.id" @click="goShop(r.merchantId || r.id)">
        <image class="result-img" :src="r.image || defaultImg" mode="aspectFill" />
        <view class="result-info">
          <text class="result-name">{{ r.name }}</text>
          <text class="result-shop">🏪 {{ r.merchantName || '店铺' }}</text>
          <text class="result-reason">{{ r.reason || '为您找到的相关菜品' }}</text>
          <text class="result-price">¥{{ r.price }}</text>
        </view>
      </view>
    </view>

    <view class="empty" v-if="results.length === 0 && searched">
      <text>未找到相关结果</text>
      <text class="empty-hint">试试其他关键词吧~</text>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { searchDish } from '@/api/index.js'

const defaultImg = '/static/icons/dish-default.png'
const keyword = ref('')
const results = ref([])
const searched = ref(false)
const history = ref(uni.getStorageSync('searchHistory') ? JSON.parse(uni.getStorageSync('searchHistory')) : [])

// 支持从标签/外部带 keyword 跳转并自动搜索
onLoad((options) => {
  if (options?.keyword) {
    keyword.value = options.keyword
    search()
  }
})

const hotTags = ['火锅', '日料', '约会约会', '性价比高', '甜品', '深夜食堂']

const quickSearch = (kw) => {
  keyword.value = kw
  search()
}

const search = async () => {
  if (!keyword.value.trim()) return
  searched.value = true

  // 保存搜索历史
  const h = history.value
  if (!h.includes(keyword.value)) {
    h.unshift(keyword.value)
    if (h.length > 10) h.pop()
    uni.setStorageSync('searchHistory', JSON.stringify(h))
  }

  try {
    const result = await searchDish(keyword.value)
    if (typeof result === 'string') {
      results.value = JSON.parse(result || '[]')
    } else {
      results.value = result || []
    }
  } catch (e) {
    results.value = []
    uni.showToast({ title: e.message || '搜索失败，请稍后再试', icon: 'none' })
  }
}

const goShop = (id) => uni.navigateTo({ url: '/pages/shop/shop?id=' + id })
</script>

<style lang="scss" scoped>
.search-page { min-height: 100vh; background: #fff; }
.search-bar { display: flex; align-items: center; padding: 16rpx 24rpx; background: #fff; }
.search-input { flex: 1; height: 72rpx; background: #f5f5f5; border-radius: 36rpx; padding: 0 24rpx; font-size: 28rpx; }
.search-btn { margin-left: 16rpx; font-size: 28rpx; color: $primary-color; font-weight: 600; }
.section-title { font-size: 28rpx; font-weight: 600; display: block; margin-bottom: 16rpx; }
.hot-section, .history-section { padding: 24rpx; }
.hot-tags, .history-tags { display: flex; flex-wrap: wrap; gap: 16rpx; }
.tag { padding: 12rpx 24rpx; background: #f5f5f5; border-radius: 20rpx; font-size: 26rpx; color: #666; }
.results { padding: 24rpx; }
.result-count { font-size: 24rpx; color: #999; display: block; margin-bottom: 16rpx; }
.result-item { display: flex; padding: 16rpx 0; border-bottom: 1rpx solid #f5f5f5; }
.result-img { width: 120rpx; height: 120rpx; border-radius: 8rpx; margin-right: 16rpx; background: #f0f0f0; }
.result-info { flex: 1; }
.result-name { font-size: 30rpx; font-weight: 600; display: block; }
.result-shop { font-size: 22rpx; color: #999; display: block; margin-top: 2rpx; }
.result-reason { font-size: 24rpx; color: $warning-color; display: block; margin: 4rpx 0; }
.result-price { font-size: 32rpx; font-weight: bold; color: $primary-color; }
.empty { text-align: center; padding: 120rpx 0; color: #999; font-size: 28rpx; }
.empty-hint { font-size: 24rpx; color: #ccc; display: block; margin-top: 8rpx; }
</style>
