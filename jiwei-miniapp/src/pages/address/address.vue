<template>
  <view class="address-page">
    <view class="addr-list">
      <view class="addr-card" v-for="a in list" :key="a.id" @click="selectAddr(a)">
        <view class="addr-top">
          <text class="addr-contact">{{ a.consignee }} {{ a.phone }}</text>
          <text class="default-tag" v-if="a.isDefault === 1">默认</text>
        </view>
        <text class="addr-detail">{{ a.provinceName }}{{ a.cityName }}{{ a.districtName }}{{ a.detail }}</text>
        <view class="addr-actions">
          <text class="action" @click.stop="setDefault(a)" v-if="a.isDefault !== 1">设为默认</text>
          <text class="action" @click.stop="edit(a)">编辑</text>
          <text class="action danger" @click.stop="remove(a)">删除</text>
        </view>
      </view>
      <view class="empty" v-if="list.length === 0">暂无收货地址</view>
    </view>

    <button class="add-btn" @click="add">+ 新增收货地址</button>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getAddressList, deleteAddress, setDefaultAddress } from '@/api/index.js'

const list = ref([])
// 从下单页跳入时可选地址返回
const selectMode = ref(false)

const load = async () => {
  try {
    list.value = await getAddressList() || []
  } catch (e) { console.log('加载地址失败', e) }
}

const selectAddr = (a) => {
  const pages = getCurrentPages()
  const cur = pages[pages.length - 1]
  if (cur.$page?.options?.select === '1') {
    // 选择模式：把地址存全局事件回传
    const eventChannel = cur.getOpenerEventChannel
      ? cur.getOpenerEventChannel()
      : null
    if (eventChannel) {
      eventChannel.emit('selectAddress', a)
    }
    uni.navigateBack()
  }
}

const add = () => uni.navigateTo({ url: '/pages/address/edit' })
const edit = (a) => uni.navigateTo({ url: '/pages/address/edit?id=' + a.id })

const setDefault = async (a) => {
  try {
    await setDefaultAddress(a.id)
    uni.showToast({ title: '已设为默认', icon: 'success' })
    load()
  } catch (e) { uni.showToast({ title: e.message || '操作失败', icon: 'none' }) }
}

const remove = (a) => {
  uni.showModal({
    title: '删除地址',
    content: '确定删除该地址吗？',
    success: async (res) => {
      if (res.confirm) {
        try {
          await deleteAddress(a.id)
          uni.showToast({ title: '已删除', icon: 'success' })
          load()
        } catch (e) { uni.showToast({ title: e.message || '删除失败', icon: 'none' }) }
      }
    }
  })
}

onMounted(load)
</script>

<style lang="scss" scoped>
.address-page { min-height: 100vh; background: $bg-color; padding: 24rpx; padding-bottom: 140rpx; }
.addr-card {
  background: #fff; border-radius: 16rpx; padding: 24rpx;
  margin-bottom: 16rpx; box-shadow: $shadow;
}
.addr-top { display: flex; align-items: center; margin-bottom: 8rpx; }
.addr-contact { font-size: 30rpx; font-weight: 600; margin-right: 12rpx; }
.default-tag {
  font-size: 20rpx; color: $primary-color; border: 1rpx solid $primary-color;
  padding: 0 10rpx; border-radius: 6rpx;
}
.addr-detail { font-size: 26rpx; color: #666; display: block; }
.addr-actions { display: flex; gap: 24rpx; justify-content: flex-end; margin-top: 16rpx; }
.action { font-size: 24rpx; color: $primary-color; }
.action.danger { color: $error-color; }
.add-btn {
  position: fixed; bottom: 40rpx; left: 24rpx; right: 24rpx;
  height: 88rpx; line-height: 88rpx; text-align: center;
  background: linear-gradient(135deg, $primary-color, $primary-light);
  color: #fff; border-radius: 44rpx; font-size: 30rpx; border: none;
}
.empty { text-align: center; padding: 120rpx 0; color: #999; font-size: 28rpx; }
</style>
