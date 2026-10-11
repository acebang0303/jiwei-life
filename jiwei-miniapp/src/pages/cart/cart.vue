<template>
  <view class="cart-page">
    <view class="cart-list" v-if="cartStore.items.length > 0">
      <view class="cart-item" v-for="item in cartStore.items" :key="item.dishId || item.setmealId">
        <image class="item-img" :src="item.image || defaultImg" mode="aspectFill" />
        <view class="item-info">
          <text class="item-name">{{ item.name }}</text>
          <text class="item-spec" v-if="item.dishFlavor">{{ item.dishFlavor }}</text>
          <text class="item-price">¥{{ item.amount }}</text>
        </view>
        <view class="item-actions">
          <view class="num-control">
            <text class="num-btn" @click="minus(item)">-</text>
            <text class="num-val">{{ item.number }}</text>
            <text class="num-btn" @click="plus(item)">+</text>
          </view>
          <text class="item-subtotal">¥{{ (item.amount * item.number).toFixed(2) }}</text>
        </view>
      </view>
    </view>

    <view class="cart-empty" v-else>
      <text class="empty-icon">🛒</text>
      <text class="empty-text">购物车空空如也</text>
      <text class="empty-hint">快去首页逛逛吧~</text>
      <button class="go-shop-btn" @click="goHome">去逛逛</button>
    </view>

    <!-- 预计优惠 -->
    <view class="coupon-row" v-if="cartStore.items.length > 0">
      <text>🎫 可用优惠券</text>
      <text class="coupon-num">0 张可用 →</text>
    </view>

    <!-- 底部结算栏 -->
    <view class="settle-bar" v-if="cartStore.items.length > 0">
      <view class="settle-left">
        <text class="total-label">合计：</text>
        <text class="total-price">¥{{ cartStore.totalPrice.toFixed(2) }}</text>
      </view>
      <button class="submit-btn" @click="checkout">去结算</button>
    </view>
  </view>
</template>

<script setup>
import { onShow } from '@dcloudio/uni-app'
import { useCartStore } from '@/store/cart.js'

const cartStore = useCartStore()
const defaultImg = '/static/icons/dish-default.png'

// 购物车 tab 每次进入都拉取最新数据（Redis 暂存）
onShow(() => {
  cartStore.fetchCart()
})

const minus = (item) => {
  const field = item.dishId ? (item.dishId + '_' + (item.dishFlavor || '')) : (item.setmealId + '_')
  const newNum = item.number - 1
  if (newNum <= 0) {
    cartStore.remove(field)
  } else {
    cartStore.updateNum(field, newNum)
  }
}

const plus = (item) => {
  const field = item.dishId ? (item.dishId + '_' + (item.dishFlavor || '')) : (item.setmealId + '_')
  cartStore.updateNum(field, item.number + 1)
}

const goHome = () => uni.switchTab({ url: '/pages/index/index' })

// 去确认订单页（选地址 → 提交）
const checkout = async () => {
  uni.navigateTo({ url: '/pages/order/submit' })
}
</script>

<style lang="scss" scoped>
.cart-page { min-height: 100vh; background: $bg-color; padding-bottom: 140rpx; }
.cart-item {
  display: flex; align-items: center; padding: 20rpx 24rpx;
  background: #fff; margin-bottom: 2rpx;
}
.item-img { width: 100rpx; height: 100rpx; border-radius: 8rpx; margin-right: 16rpx; background: #f0f0f0; }
.item-info { flex: 1; }
.item-name { font-size: 28rpx; font-weight: 600; display: block; }
.item-spec { font-size: 22rpx; color: #999; display: block; }
.item-price { font-size: 26rpx; color: $primary-color; font-weight: 600; }
.item-actions { text-align: right; }
.num-control { display: flex; align-items: center; gap: 16rpx; }
.num-btn {
  width: 40rpx; height: 40rpx; line-height: 40rpx; text-align: center;
  border-radius: 50%; border: 1rpx solid #ddd; font-size: 28rpx;
  color: #666;
}
.num-val { font-size: 28rpx; font-weight: 600; min-width: 40rpx; text-align: center; }
.item-subtotal { font-size: 24rpx; color: $primary-color; margin-top: 8rpx; display: block; }
.cart-empty { text-align: center; padding: 160rpx 0; }
.empty-icon { font-size: 100rpx; display: block; }
.empty-text { font-size: 32rpx; color: #999; display: block; margin-top: 16rpx; }
.empty-hint { font-size: 26rpx; color: #ccc; display: block; margin-top: 8rpx; }
.go-shop-btn {
  margin-top: 40rpx; width: 240rpx; height: 72rpx; line-height: 72rpx;
  background: $primary-color; color: #fff; border-radius: 36rpx; font-size: 28rpx;
  border: none;
}
.coupon-row { display: flex; justify-content: space-between; padding: 20rpx 24rpx; background: #fff; margin-top: 16rpx; font-size: 26rpx; }
.coupon-num { color: #999; }
.settle-bar {
  position: fixed; bottom: 0; left: 0; right: 0;
  display: flex; align-items: center; padding: 16rpx 24rpx;
  background: #fff; box-shadow: 0 -2rpx 12rpx rgba(0,0,0,0.08);
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
}
.settle-left { flex: 1; }
.total-label { font-size: 28rpx; color: #666; }
.total-price { font-size: 40rpx; font-weight: bold; color: $primary-color; }
.submit-btn {
  height: 80rpx; line-height: 80rpx; padding: 0 48rpx;
  background: linear-gradient(135deg, $primary-color, $primary-light);
  color: #fff; border-radius: 40rpx; font-size: 30rpx; border: none;
}
</style>
