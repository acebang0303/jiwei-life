<template>
  <view class="order-page">
    <!-- 状态Tab -->
    <view class="order-tabs">
      <view class="tab" v-for="t in tabs" :key="t.key"
            :class="{ active: activeTab === t.key }"
            @click="switchTab(t.key)">
        {{ t.label }}
      </view>
    </view>

    <!-- 订单列表 -->
    <view class="order-list">
      <view class="order-card" v-for="o in orders" :key="o.id" @click="goDetail(o.id)">
        <view class="order-header">
          <text class="order-shop">{{ o.merchantName || '店铺' }}</text>
          <text class="order-status" :style="{ color: statusMap[o.status]?.color }">
            {{ statusMap[o.status]?.text || '未知' }}
          </text>
        </view>
        <view class="order-items">
          <view class="order-item" v-for="d in (o.orderDetails || [])" :key="d.id">
            <image class="item-img" :src="d.image || defaultImg" mode="aspectFill" />
            <text class="item-name">{{ d.name }}</text>
            <text class="item-num">x{{ d.number }}</text>
          </view>
        </view>
        <view class="order-footer">
          <text class="order-time">{{ formatTime(o.createTime) }}</text>
          <text class="order-amount">共{{ o.orderDetails?.length || 0 }}件  ¥{{ o.payAmount || o.amount }}</text>
        </view>
        <view class="order-actions" v-if="o.status === 1">
          <button class="action-btn cancel" @click.stop="cancel(o.id)">取消订单</button>
          <button class="action-btn pay" @click.stop="pay(o)">立即支付</button>
        </view>
      </view>
    </view>

    <view class="empty" v-if="orders.length === 0">
      <text>暂无订单</text>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getOrderPage, cancelOrder, payOrder, mockPayConfirm } from '@/api/index.js'
import { orderStatusMap, formatTime } from '@/utils/index.js'

const defaultImg = '/static/icons/dish-default.png'
const statusMap = orderStatusMap
const activeTab = ref(0)
const orders = ref([])

const tabs = [
  { key: 0, label: '全部' },
  { key: 1, label: '待支付' },
  { key: 2, label: '待接单' },
  { key: 3, label: '备货中' },
  { key: 5, label: '已完成' }
]

const switchTab = (key) => {
  activeTab.value = key
  loadOrders()
}

const loadOrders = async () => {
  try {
    const result = await getOrderPage(1, 20, activeTab.value || undefined)
    orders.value = result?.records || []
  } catch (e) {
    console.log('加载订单失败', e)
  }
}

const cancel = async (id) => {
  uni.showModal({
    title: '取消订单',
    content: '确定要取消这个订单吗？',
    success: async (res) => {
      if (res.confirm) {
        try {
          await cancelOrder(id)
          uni.showToast({ title: '已取消', icon: 'success' })
          loadOrders()
        } catch (e) {
          uni.showToast({ title: e.message, icon: 'none' })
        }
      }
    }
  })
}

const pay = async (o) => {
  uni.showLoading({ title: '支付中...' })
  try {
    await payOrder(o.id, 'wechat')        // 发起微信支付（mock 渠道）
    await mockPayConfirm(o.number)         // 模拟微信回调确认入账
    uni.hideLoading()
    uni.showToast({ title: '支付成功！', icon: 'success' })
    loadOrders()
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: e.message || '支付失败', icon: 'none' })
  }
}

const goDetail = (id) => uni.navigateTo({ url: '/pages/order/detail?id=' + id })

onMounted(() => loadOrders())
</script>

<style lang="scss" scoped>
.order-page { min-height: 100vh; background: $bg-color; }
.order-tabs { display: flex; background: #fff; padding: 0 24rpx; border-bottom: 1rpx solid #f0f0f0; }
.tab { flex: 1; text-align: center; padding: 24rpx 0; font-size: 28rpx; color: #666; }
.tab.active { color: $primary-color; font-weight: 600; position: relative; }
.tab.active::after {
  content: ''; position: absolute; bottom: 4rpx; left: 50%;
  transform: translateX(-50%); width: 40rpx; height: 4rpx;
  background: $primary-color; border-radius: 2rpx;
}
.order-list { padding: 24rpx; }
.order-card { background: #fff; border-radius: 16rpx; padding: 20rpx; margin-bottom: 16rpx; box-shadow: $shadow; }
.order-header { display: flex; justify-content: space-between; margin-bottom: 12rpx; }
.order-shop { font-size: 28rpx; font-weight: 600; }
.order-status { font-size: 26rpx; font-weight: 500; }
.order-items{ border-top: 1rpx solid #f0f0f0; padding-top: 12rpx; }
.order-item { display: flex; align-items: center; padding: 8rpx 0; }
.item-img { width: 64rpx; height: 64rpx; border-radius: 6rpx; margin-right: 12rpx; background: #f0f0f0; }
.item-name { flex: 1; font-size: 26rpx; }
.item-num { font-size: 24rpx; color: #999; }
.order-footer { display: flex; justify-content: space-between; padding-top: 12rpx; border-top: 1rpx solid #f0f0f0; margin-top: 8rpx; font-size: 24rpx; color: #999; }
.order-actions { display: flex; justify-content: flex-end; gap: 16rpx; margin-top: 16rpx; }
.action-btn { height: 56rpx; line-height: 56rpx; padding: 0 24rpx; font-size: 24rpx; border-radius: 28rpx; border: none; }
.action-btn.cancel { background: #f5f5f5; color: #666; }
.action-btn.pay { background: $primary-color; color: #fff; }
.empty { text-align: center; padding: 160rpx 0; color: #999; font-size: 28rpx; }
</style>
