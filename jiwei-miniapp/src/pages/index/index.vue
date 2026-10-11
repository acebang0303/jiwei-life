<template>
  <view class="home">
    <!-- 顶部定位栏 -->
    <view class="header">
      <view class="location" @click="chooseLocation">
        <text class="iconfont">📍</text>
        <text class="city">{{ currentCity }}</text>
      </view>
      <view class="search-bar" @click="goSearch">
        <text class="search-icon">🔍</text>
        <text class="search-placeholder">AI智能搜索美食...</text>
      </view>
      <view class="header-right">
        <text class="ai-badge" @click="goAiChat">AI</text>
      </view>
    </view>

    <!-- 轮播活动 -->
    <swiper class="banner-swiper" :indicator-dots="true" autoplay circular>
      <swiper-item v-for="b in banners" :key="b.id">
        <view class="banner" :style="{ background: b.bgColor || 'linear-gradient(135deg, #FF7A3D, #F06A2E)' }" @click="goActivity(b)">
          <text class="banner-title">{{ b.title }}</text>
          <text class="banner-sub">{{ b.subtitle }}</text>
        </view>
      </swiper-item>
    </swiper>

    <!-- 快捷入口 -->
    <view class="quick-entry">
      <view class="entry-item" v-for="e in entries" :key="e.key" @click="e.action">
        <view class="entry-icon">{{ e.icon }}</view>
        <text class="entry-text">{{ e.text }}</text>
      </view>
    </view>

    <!-- 秒杀活动 -->
    <view class="section" v-if="seckillList.length > 0">
      <view class="section-header">
        <text class="section-title">⏰ 限时秒杀</text>
        <text class="section-more" @click="goSeckill">更多 →</text>
      </view>
      <scroll-view scroll-x class="seckill-scroll">
        <view class="seckill-item" v-for="sk in seckillList" :key="sk.id" @click="goSeckill">
          <text class="seckill-price">¥{{ sk.seckillPrice }}</text>
          <text class="seckill-name">{{ sk.dishName }}</text>
          <text class="seckill-stock">仅剩{{ sk.stock }}份</text>
          <button class="seckill-btn" @click.stop="goSeckill">抢购</button>
        </view>
      </scroll-view>
    </view>

    <!-- 附近店铺 -->
    <view class="section">
      <view class="section-header">
        <text class="section-title">🏪 附近好店</text>
      </view>
      <view class="shop-list">
        <view class="shop-card" v-for="shop in shopList" :key="shop.id" @click="goShop(shop.id)">
          <image class="shop-logo" :src="shop.logo || defaultLogo" mode="aspectFill" />
          <view class="shop-info">
            <text class="shop-name">{{ shop.name }}</text>
            <view class="shop-tags">
              <text class="shop-tag star">★ 4.8</text>
              <text class="shop-tag">{{ shop.deliveryRange ? (shop.deliveryRange/1000).toFixed(1) + 'km' : '3km' }}</text>
              <text class="shop-tag">{{ shop.businessHours || '营业中' }}</text>
            </view>
            <text class="shop-desc">{{ shop.description || '品质美食，值得信赖' }}</text>
          </view>
          <view class="shop-arrow">→</view>
        </view>
      </view>
    </view>

    <!-- AI推荐菜品 -->
    <view class="section" v-if="aiDishes.length > 0">
      <view class="section-header">
        <text class="section-title">🤖 猜你喜欢（根据你的浏览和点赞）</text>
      </view>
      <scroll-view scroll-x class="dish-scroll">
        <view class="dish-card" v-for="d in aiDishes" :key="d.dishId || d.id" @click="goDish(d)">
          <image class="dish-img" :src="d.image || defaultImg" mode="aspectFill" />
          <text class="dish-name">{{ d.name }}</text>
          <text class="dish-shop">{{ d.merchantName }}</text>
          <text class="dish-reason">{{ d.reason }}</text>
          <text class="dish-price">¥{{ d.price }}</text>
        </view>
      </scroll-view>
    </view>

    <!-- 底部安全区 -->
    <view class="safe-bottom" />
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getMerchantList, getSeckillList, getAiRecommend, getActivityList, signToday, getSignToday, reportBehavior } from '@/api/index.js'
import { useUserStore } from '@/store/user.js'

const userStore = useUserStore()
const defaultLogo = '/static/icons/shop-default.png'
const defaultImg = '/static/icons/dish-default.png'
const currentCity = ref('杭州')
const shopList = ref([])
const seckillList = ref([])
const aiDishes = ref([])
const banners = ref([])
const signedToday = ref(false)

const entries = ref([])

const buildEntries = () => {
  entries.value = [
    { key: 'seckill', icon: '⚡', text: '限时秒杀', action: () => goSeckill() },
    { key: 'coupon', icon: '🎫', text: '优惠券', action: () => goCoupon() },
    { key: 'sign', icon: '📅', text: signedToday.value ? '已签到' : '签到', action: () => doSign() },
    { key: 'order', icon: '📋', text: '订单', action: () => goOrder() }
  ]
}

const chooseLocation = () => {
  uni.getLocation({
    type: 'gcj02',
    success: () => { currentCity.value = '杭州市' },
    fail: () => { /* 默认杭州 */ }
  })
}

const goSearch = () => uni.navigateTo({ url: '/pages/search/search' })
const goAiChat = () => uni.navigateTo({ url: '/pages/aichat/aichat' })
const goShop = (id) => {
  if (userStore.isLogin) reportBehavior('VIEW_MERCHANT', id).catch(() => {})
  uni.navigateTo({ url: '/pages/shop/shop?id=' + id })
}
const goDish = (d) => {
  if (userStore.isLogin) reportBehavior('VIEW_MERCHANT', d.merchantId).catch(() => {})
  uni.navigateTo({ url: '/pages/shop/shop?id=' + d.merchantId })
}
const goSeckill = () => uni.navigateTo({ url: '/pages/seckill/seckill' })
const goCoupon = () => uni.navigateTo({ url: '/pages/coupon/coupon' })
const goOrder = () => uni.navigateTo({ url: '/pages/order/order' })

// banner 活动跳转：1=店铺 2=秒杀 3=优惠券 4=笔记详情
const goActivity = (b) => {
  switch (b.type) {
    case 1: uni.navigateTo({ url: '/pages/shop/shop?id=' + b.targetId }); break
    case 2: uni.navigateTo({ url: '/pages/seckill/seckill' }); break
    case 3: uni.navigateTo({ url: '/pages/coupon/coupon' }); break
    case 4: uni.navigateTo({ url: '/pages/note/detail?id=' + b.targetId }); break
    default: break
  }
}

const doSign = async () => {
  if (!userStore.isLogin) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  try {
    await signToday()
    signedToday.value = true
    buildEntries()
    uni.showToast({ title: '签到成功！+5成长值', icon: 'success' })
  } catch (e) {
    if (e.message && e.message.includes('已签到')) {
      signedToday.value = true
      buildEntries()
    }
    uni.showToast({ title: e.message || '签到失败', icon: 'none' })
  }
}

const loadSigned = async () => {
  if (!userStore.isLogin) return
  try {
    const today = await getSignToday()
    signedToday.value = today?.signed || false
  } catch (e) { /* 忽略 */ }
}

onShow(() => {
  buildEntries()
  loadSigned()
})

onMounted(async () => {
  chooseLocation()
  try {
    banners.value = await getActivityList() || []
  } catch (e) { console.log('加载活动失败', e) }
  try {
    shopList.value = await getMerchantList() || []
  } catch (e) { console.log('加载店铺失败', e) }
  try {
    seckillList.value = await getSeckillList() || []
  } catch (e) { console.log('加载秒杀失败', e) }
  try {
    const uid = uni.getStorageSync('userInfo')?.id || 1
    const res = await getAiRecommend(uid, 8)
    if (typeof res === 'string') {
      aiDishes.value = JSON.parse(res || '[]')
    } else {
      aiDishes.value = res || []
    }
  } catch (e) { console.log('AI推荐加载失败', e) }
})
</script>

<style lang="scss" scoped>
.home { padding-bottom: 120rpx; }
.header {
  display: flex; align-items: center; padding: 24rpx 24rpx 16rpx;
  background: #fff; position: sticky; top: 0; z-index: 100;
  box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.04);
}
.location { display: flex; align-items: center; margin-right: 16rpx; }
.city { font-size: 28rpx; font-weight: 600; white-space: nowrap; }
.search-bar {
  flex: 1; display: flex; align-items: center; height: 64rpx;
  background: #f5f5f5; border-radius: 32rpx; padding: 0 20rpx;
}
.search-icon { margin-right: 8rpx; }
.search-placeholder { color: #999; font-size: 26rpx; }
.header-right { margin-left: 16rpx; }
.ai-badge {
  background: linear-gradient(135deg, $glow-color, $primary-color);
  color: #fff; padding: 4rpx 12rpx; border-radius: 8rpx;
  font-size: 22rpx; font-weight: bold;
}
.banner-swiper { height: 280rpx; margin: 16rpx 24rpx; border-radius: 16rpx; overflow: hidden; }
.banner {
  height: 100%; display: flex; flex-direction: column; justify-content: center;
  padding: 40rpx;
}
.banner-title { color: #fff; font-size: 40rpx; font-weight: bold; }
.banner-sub { color: rgba(255,255,255,0.9); font-size: 26rpx; margin-top: 8rpx; }
.quick-entry {
  display: flex; justify-content: space-around; padding: 32rpx 24rpx;
  background: #fff; margin: 0 24rpx; border-radius: 16rpx;
}
.entry-item { text-align: center; }
.entry-icon { font-size: 48rpx; margin-bottom: 8rpx; }
.entry-text { font-size: 24rpx; color: #666; }
.section { margin: 24rpx; }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16rpx; }
.section-title { font-size: 32rpx; font-weight: bold; }
.section-more { font-size: 26rpx; color: $primary-color; }
.seckill-scroll { white-space: nowrap; }
.seckill-item {
  display: inline-block; width: 200rpx; padding: 20rpx;
  background: #FFF7F3; border-radius: 12rpx; margin-right: 16rpx;
  text-align: center; border: 1rpx solid #FFE0D0;
}
.seckill-price { font-size: 36rpx; font-weight: bold; color: $primary-color; display: block; }
.seckill-name { font-size: 24rpx; color: #333; display: block; margin: 4rpx 0; }
.seckill-stock { font-size: 20rpx; color: #999; display: block; }
.seckill-btn {
  margin-top: 12rpx; height: 48rpx; line-height: 48rpx; font-size: 22rpx;
  background: $primary-color; color: #fff; border-radius: 24rpx;
}
.shop-card {
  display: flex; align-items: center; padding: 20rpx;
  background: #fff; border-radius: 16rpx; margin-bottom: 16rpx;
  box-shadow: $shadow;
}
.shop-logo { width: 100rpx; height: 100rpx; border-radius: 12rpx; margin-right: 16rpx; background: #f0f0f0; }
.shop-info { flex: 1; }
.shop-name { font-size: 30rpx; font-weight: 600; display: block; margin-bottom: 6rpx; }
.shop-tags { display: flex; gap: 12rpx; margin-bottom: 6rpx; }
.shop-tag { font-size: 22rpx; color: #666; background: #f5f5f5; padding: 2rpx 8rpx; border-radius: 4rpx; }
.shop-tag.star { color: $warning-color; }
.shop-desc { font-size: 24rpx; color: #999; @extend .text-ellipsis; }
.shop-arrow { font-size: 32rpx; color: #ccc; }
.dish-scroll { white-space: nowrap; }
.dish-card {
  display: inline-block; width: 240rpx; margin-right: 16rpx;
  background: #fff; border-radius: 12rpx; overflow: hidden;
  box-shadow: $shadow; vertical-align: top;
}
.dish-img { width: 240rpx; height: 160rpx; background: #f0f0f0; }
.dish-name { font-size: 26rpx; font-weight: 600; padding: 8rpx 12rpx 0; display: block; }
.dish-shop { font-size: 20rpx; color: #999; padding: 4rpx 12rpx 0; display: block; }
.dish-reason { font-size: 20rpx; color: $warning-color; padding: 4rpx 12rpx; display: block;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dish-price { font-size: 28rpx; font-weight: bold; color: $primary-color; padding: 0 12rpx 12rpx; display: block; }
.safe-bottom { height: calc(120rpx + env(safe-area-inset-bottom)); }
</style>
