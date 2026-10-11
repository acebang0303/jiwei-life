<template>
  <view class="seckill-page">
    <!-- 秒杀横幅 -->
    <view class="seckill-banner">
      <text class="banner-title">⚡ 限时秒杀</text>
      <text class="banner-sub">整点开抢 · 手慢无</text>
    </view>

    <!-- 秒杀列表 -->
    <view class="seckill-list">
      <view class="seckill-card" v-for="sk in list" :key="sk.id">
        <view class="seckill-left">
          <text class="activity-name">{{ sk.name }}</text>
          <text class="dish-name">🏪 {{ sk.merchantName || '' }} · {{ sk.dishName || '秒杀商品' }}</text>
          <view class="price-row">
            <text class="price">¥{{ sk.seckillPrice }}</text>
            <text class="origin-price">¥{{ sk.originalPrice || '' }}</text>
          </view>
        </view>
        <view class="seckill-right">
          <view class="stock-info">
            <text class="stock">仅剩 {{ sk.stock }} 份</text>
            <text class="limit">限购{{ sk.limitPerUser }}份/人</text>
          </view>
          <button class="buy-btn" :class="{ soldout: sk.stock <= 0 }"
                  :disabled="sk.stock <= 0 || buying" @click="buy(sk)">
            {{ sk.stock <= 0 ? '已抢光' : '立即抢购' }}
          </button>
        </view>
      </view>
    </view>

    <view class="empty" v-if="list.length === 0">暂无进行中的秒杀活动</view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getSeckillList, buySeckill } from '@/api/index.js'
import { useUserStore } from '@/store/user.js'

const userStore = useUserStore()
const list = ref([])
const buying = ref(false)

const load = async () => {
  try {
    const raw = await getSeckillList() || []
    // 补充菜品名/原价展示（后端已关联）
    list.value = raw
  } catch (e) { console.log('加载秒杀失败', e) }
}

const buy = async (sk) => {
  if (!userStore.isLogin) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  buying.value = true
  try {
    const orderId = await buySeckill(sk.id, sk.dishId)
    uni.showModal({
      title: '抢购成功！',
      content: '订单号：' + orderId + '，请在订单列表中完成支付',
      showCancel: false,
      confirmText: '去订单列表',
      success: () => {
        uni.navigateTo({ url: '/pages/order/order' })
      }
    })
    load()
  } catch (e) {
    uni.showToast({ title: e.message || '抢购失败', icon: 'none' })
  } finally {
    buying.value = false
  }
}

onMounted(load)
</script>

<style lang="scss" scoped>
.seckill-page { min-height: 100vh; background: $bg-color; padding-bottom: 60rpx; }
.seckill-banner {
  padding: 48rpx 32rpx;
  background: linear-gradient(135deg, #FF5E2E, #FF9A5A);
}
.banner-title { color: #fff; font-size: 44rpx; font-weight: bold; display: block; }
.banner-sub { color: rgba(255,255,255,0.85); font-size: 26rpx; margin-top: 8rpx; display: block; }
.seckill-list { padding: 24rpx; }
.seckill-card {
  display: flex; justify-content: space-between; align-items: center;
  background: #fff; border-radius: 16rpx; padding: 24rpx;
  margin-bottom: 16rpx; box-shadow: $shadow;
}
.seckill-left { flex: 1; }
.activity-name { font-size: 30rpx; font-weight: bold; display: block; }
.dish-name { font-size: 26rpx; color: #666; margin-top: 6rpx; display: block; }
.price-row { display: flex; align-items: baseline; gap: 12rpx; margin-top: 10rpx; }
.price { font-size: 40rpx; font-weight: bold; color: $primary-color; }
.origin-price { font-size: 24rpx; color: #ccc; text-decoration: line-through; }
.seckill-right { text-align: right; }
.stock-info { margin-bottom: 12rpx; }
.stock { font-size: 22rpx; color: $error-color; display: block; }
.limit { font-size: 20rpx; color: #999; display: block; margin-top: 2rpx; }
.buy-btn {
  width: 180rpx; height: 64rpx; line-height: 64rpx;
  background: linear-gradient(135deg, $primary-color, $primary-light);
  color: #fff; border-radius: 32rpx; font-size: 26rpx; border: none;
}
.buy-btn.soldout { background: #ccc; }
.empty { text-align: center; padding: 120rpx 0; color: #999; font-size: 28rpx; }
</style>
