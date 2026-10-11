<template>
  <view class="submit-page">
    <view class="addr-section" @click="chooseAddr">
      <view class="addr-info" v-if="address">
        <text class="addr-contact">{{ address.consignee }} {{ address.phone }}</text>
        <text class="addr-detail">{{ address.provinceName }}{{ address.cityName }}{{ address.districtName }}{{ address.detail }}</text>
      </view>
      <view class="addr-empty" v-else>
        <text>请选择收货地址</text>
      </view>
      <text class="addr-arrow">→</text>
    </view>

    <view class="goods-section">
      <text class="section-title">商品明细</text>
      <view class="goods-item" v-for="item in items" :key="item.dishId || item.setmealId">
        <view class="goods-left">
          <text>{{ item.name }} x{{ item.number }}</text>
          <text v-if="item.dishFlavor" class="goods-flavor">{{ item.dishFlavor }}</text>
        </view>
        <text>¥{{ (item.amount * item.number).toFixed(2) }}</text>
      </view>
    </view>

    <view class="remark-section">
      <text class="section-title">订单备注</text>
      <input class="remark-input" v-model="remark" placeholder="口味、偏好等要求（选填）" />
    </view>

    <view class="total-section">
      <text>实付金额</text>
      <text class="total-price">¥{{ totalPrice }}</text>
    </view>

    <button class="pay-btn" @click="confirmPay">确认支付 ¥{{ totalPrice }}</button>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getAddressList, submitOrder } from '@/api/index.js'
import { useCartStore } from '@/store/cart.js'

const cartStore = useCartStore()
const address = ref(null)
const items = ref([])
const totalPrice = ref(0)
const remark = ref('')

const loadDefaultAddr = async () => {
  try {
    const addrs = await getAddressList()
    if (addrs && addrs.length > 0) {
      address.value = addrs.find(a => a.isDefault === 1) || addrs[0]
    }
  } catch (e) { console.log('加载地址失败', e) }
}

onMounted(async () => {
  items.value = cartStore.items
  totalPrice.value = cartStore.totalPrice.toFixed(2)
  await loadDefaultAddr()
})

// 跳地址管理页选择（select 模式），通过 eventChannel 回传
const chooseAddr = () => {
  uni.navigateTo({
    url: '/pages/address/address?select=1',
    success: (res) => {
      res.eventChannel.on('selectAddress', (data) => {
        address.value = data
      })
    }
  })
}

const confirmPay = async () => {
  if (!address.value || !address.value.id) {
    uni.showToast({ title: '请选择收货地址', icon: 'none' })
    return
  }
  if (items.value.length === 0) {
    uni.showToast({ title: '购物车为空', icon: 'none' })
    return
  }
  uni.showLoading({ title: '提交中' })
  try {
    await submitOrder({
      addressBookId: address.value.id,
      merchantId: items.value[0]?.merchantId,
      remark: remark.value,
      items: items.value.map(i => ({
        dishId: i.dishId || null,
        setmealId: i.setmealId || null,
        name: i.name,
        amount: i.amount,
        number: i.number,
        dishFlavor: i.dishFlavor || ''
      })),
      amount: Number(totalPrice.value)
    })
    uni.hideLoading()
    await cartStore.clear()
    uni.showToast({ title: '下单成功' })
    setTimeout(() => uni.redirectTo({ url: '/pages/order/order' }), 1200)
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: e.message || '下单失败', icon: 'none' })
  }
}
</script>

<style lang="scss" scoped>
.submit-page { min-height: 100vh; background: $bg-color; padding-bottom: 140rpx; }
.addr-section { display: flex; align-items: center; padding: 24rpx; background: #fff; margin: 16rpx 24rpx; border-radius: 12rpx; }
.addr-info { flex: 1; }
.addr-contact { font-size: 30rpx; font-weight: 600; display: block; }
.addr-detail { font-size: 24rpx; color: #666; margin-top: 4rpx; display: block; }
.addr-empty { flex: 1; color: #999; }
.addr-arrow { font-size: 32rpx; color: #ccc; }
.goods-section { background: #fff; margin: 0 24rpx 16rpx; border-radius: 12rpx; padding: 20rpx; }
.section-title { font-size: 28rpx; font-weight: 600; display: block; margin-bottom: 12rpx; }
.goods-item { display: flex; justify-content: space-between; padding: 8rpx 0; font-size: 26rpx; }
.goods-left { display: flex; flex-direction: column; }
.goods-flavor { font-size: 22rpx; color: #999; }
.remark-section { background: #fff; margin: 0 24rpx 16rpx; border-radius: 12rpx; padding: 20rpx; }
.remark-input { font-size: 26rpx; }
.total-section { display: flex; justify-content: space-between; background: #fff; margin: 0 24rpx; border-radius: 12rpx; padding: 20rpx; }
.total-price { font-size: 36rpx; font-weight: bold; color: $primary-color; }
.pay-btn {
  position: fixed; bottom: 40rpx; left: 24rpx; right: 24rpx;
  height: 88rpx; line-height: 88rpx; text-align: center;
  background: linear-gradient(135deg, $primary-color, $primary-light);
  color: #fff; border-radius: 44rpx; font-size: 32rpx; border: none;
}
</style>
