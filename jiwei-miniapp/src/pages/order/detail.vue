<template>
  <view class="order-detail">
    <view class="status-bar" :style="{ background: statusColor }">
      <text class="status-text">{{ statusText }}</text>
      <text class="status-hint" v-if="order.status === 1">请在15分钟内完成支付</text>
    </view>

    <view class="section">
      <text class="section-title">订单信息</text>
      <view class="info-row"><text>订单编号</text><text>{{ order.number }}</text></view>
      <view class="info-row"><text>下单时间</text><text>{{ formatTime(order.createTime) }}</text></view>
      <view class="info-row" v-if="order.addressDetail"><text>收货地址</text><text>{{ order.addressDetail }}</text></view>
      <view class="info-row" v-if="order.remark"><text>备注</text><text>{{ order.remark }}</text></view>
      <view class="info-row"><text>支付方式</text><text>微信支付</text></view>
    </view>

    <view class="section">
      <text class="section-title">菜品明细</text>
      <view class="item" v-for="d in (order.orderDetails || [])" :key="d.id">
        <view class="item-left">
          <text>{{ d.name }} x{{ d.number }}</text>
          <text v-if="d.dishFlavor" class="item-flavor">{{ d.dishFlavor }}</text>
        </view>
        <text>¥{{ d.amount }}</text>
      </view>
    </view>

    <view class="section">
      <text class="section-title">金额明细</text>
      <view class="info-row"><text>商品金额</text><text>¥{{ order.amount }}</text></view>
      <view class="info-row"><text>优惠金额</text><text>-¥{{ order.discountAmount || 0 }}</text></view>
      <view class="info-row"><text>配送费</text><text>¥{{ order.deliveryFee || 0 }}</text></view>
      <view class="info-row total"><text>实付金额</text><text>¥{{ order.payAmount || order.amount }}</text></view>
    </view>

    <view class="actions" v-if="order.status === 1">
      <button class="btn cancel" @click="cancel">取消订单</button>
      <button class="btn pay" @click="pay">立即支付</button>
    </view>
    <view class="actions" v-if="order.status === 2 || order.status === 3 || order.status === 4">
      <button class="btn remind" @click="remind">催单</button>
    </view>
    <view class="actions" v-if="order.status === 5 || order.status === 6">
      <button class="btn repeat" @click="repeat">再来一单</button>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getOrderDetail, cancelOrder, payOrder, mockPayConfirm, remindOrder, repetitionOrder } from '@/api/index.js'
import { orderStatusMap, formatTime } from '@/utils/index.js'

const order = ref({})
const statusText = computed(() => orderStatusMap[order.value.status]?.text || '')
const statusColor = computed(() => orderStatusMap[order.value.status]?.color || '#999')

onLoad((options) => {
  const id = options && options.id ? Number(options.id) : null
  if (id) {
    loadDetail(id)
  }
})

const loadDetail = async (id) => {
    try {
      const result = await getOrderDetail(id)
      if (result) order.value = result
    } catch (e) { console.log(e) }
  }

const cancel = async () => {
  uni.showModal({
    title: '取消订单',
    content: '确定要取消订单吗？',
    success: async (res) => {
      if (res.confirm) {
        await cancelOrder(order.value.id)
        uni.showToast({ title: '已取消' })
        uni.navigateBack()
      }
    }
  })
}

const pay = async () => {
  uni.showLoading({ title: '支付中...' })
  try {
    await payOrder(order.value.id, 'wechat')
    await mockPayConfirm(order.value.number)
    uni.hideLoading()
    uni.showToast({ title: '支付成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 1200)
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: e.message || '支付失败', icon: 'none' })
  }
}

const remind = async () => {
  await remindOrder(order.value.id)
  uni.showToast({ title: '已催单，商家会尽快处理', icon: 'none' })
}

const repeat = async () => {
  try {
    await repetitionOrder(order.value.id)
    uni.showModal({
      title: '已加入购物车',
      content: '原订单的菜品已重新加入购物车，去结算？',
      confirmText: '去购物车',
      success: (res) => {
        if (res.confirm) uni.switchTab({ url: '/pages/cart/cart' })
      }
    })
  } catch (e) {
    uni.showToast({ title: e.message || '操作失败', icon: 'none' })
  }
}
</script>

<style lang="scss" scoped>
.order-detail { background: $bg-color; min-height: 100vh; }
.status-bar { padding: 40rpx 24rpx; text-align: center; color: #fff; }
.status-text { font-size: 36rpx; font-weight: bold; display: block; }
.status-hint { font-size: 24rpx; opacity: 0.85; margin-top: 8rpx; display: block; }
.section { background: #fff; margin: 16rpx 24rpx; border-radius: 12rpx; padding: 20rpx; }
.section-title { font-size: 28rpx; font-weight: 600; display: block; margin-bottom: 16rpx; }
.info-row { display: flex; justify-content: space-between; padding: 8rpx 0; font-size: 26rpx; color: #666; }
.info-row.total { font-weight: bold; color: $primary-color; font-size: 30rpx; border-top: 1rpx solid #f0f0f0; padding-top: 16rpx; margin-top: 8rpx; }
.item { display: flex; justify-content: space-between; padding: 8rpx 0; font-size: 26rpx; }
.actions { display: flex; gap: 24rpx; padding: 24rpx; padding-bottom: 60rpx; }
.btn { flex: 1; height: 80rpx; line-height: 80rpx; border-radius: 40rpx; font-size: 28rpx; border: none; text-align: center; }
.btn.cancel { background: #f5f5f5; color: #666; }
.btn.pay { background: $primary-color; color: #fff; }
.btn.remind { background: $warning-color; color: #fff; }
.item { display: flex; justify-content: space-between; padding: 8rpx 0; font-size: 26rpx; }
.item-left { display: flex; flex-direction: column; }
.item-flavor { font-size: 22rpx; color: #999; }
</style>
