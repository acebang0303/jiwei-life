<template>
  <view class="addr-edit">
    <view class="form">
      <view class="form-row">
        <text class="label">收货人</text>
        <input class="input" v-model="form.consignee" placeholder="请输入收货人姓名" />
      </view>
      <view class="form-row">
        <text class="label">手机号</text>
        <input class="input" v-model="form.phone" type="number" placeholder="请输入手机号" />
      </view>
      <view class="form-row">
        <text class="label">地区</text>
        <picker mode="region" @change="onRegion" :value="region">
          <view class="input picker">{{ regionText || '请选择省市区' }}</view>
        </picker>
      </view>
      <view class="form-row">
        <text class="label">详细地址</text>
        <input class="input" v-model="form.detail" placeholder="街道、门牌号等" />
      </view>
      <view class="form-row">
        <text class="label">标签</text>
        <input class="input" v-model="form.label" placeholder="如：家 / 公司" />
      </view>
    </view>

    <button class="save-btn" @click="save">保存</button>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getAddressList, addAddress, updateAddress } from '@/api/index.js'

const editId = ref(null)
const form = ref({ consignee: '', phone: '', provinceName: '', cityName: '', districtName: '', provinceCode: '', cityCode: '', districtCode: '', detail: '', label: '' })
const region = ref([])
const regionText = ref('')

const onRegion = (e) => {
  region.value = e.detail.value
  form.value.provinceName = e.detail.value[0] || ''
  form.value.cityName = e.detail.value[1] || ''
  form.value.districtName = e.detail.value[2] || ''
  form.value.provinceCode = (e.detail.code && e.detail.code[0]) || ''
  form.value.cityCode = (e.detail.code && e.detail.code[1]) || ''
  form.value.districtCode = (e.detail.code && e.detail.code[2]) || ''
  regionText.value = e.detail.value.join(' ')
}

const loadDetail = async () => {
  try {
    const list = await getAddressList() || []
    const target = list.find(a => a.id === editId.value)
    if (target) {
      form.value = {
        consignee: target.consignee, phone: target.phone,
        provinceName: target.provinceName, cityName: target.cityName,
        districtName: target.districtName,
        provinceCode: target.provinceCode, cityCode: target.cityCode, districtCode: target.districtCode,
        detail: target.detail, label: target.label
      }
      region.value = [target.provinceName, target.cityName, target.districtName]
      regionText.value = [target.provinceName, target.cityName, target.districtName].filter(Boolean).join(' ')
    }
  } catch (e) { console.log(e) }
}

const save = async () => {
  if (!form.value.consignee || !form.value.phone || !form.value.provinceName || !form.value.detail) {
    uni.showToast({ title: '请填写完整信息（含地区）', icon: 'none' })
    return
  }
  uni.showLoading({ title: '保存中' })
  try {
    if (editId.value) {
      await updateAddress(editId.value, form.value)
    } else {
      await addAddress(form.value)
    }
    uni.hideLoading()
    uni.showToast({ title: '保存成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 800)
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: e.message || '保存失败', icon: 'none' })
  }
}

// 用 onLoad 取页面参数（setup 阶段取不到 $page.options）
onLoad((options) => {
  editId.value = options && options.id ? Number(options.id) : null
  if (editId.value) loadDetail()
})
</script>

<style lang="scss" scoped>
.addr-edit { min-height: 100vh; background: $bg-color; padding: 24rpx; padding-bottom: 140rpx; }
.form { background: #fff; border-radius: 16rpx; padding: 0 24rpx; }
.form-row { display: flex; align-items: center; padding: 28rpx 0; border-bottom: 1rpx solid #f5f5f5; }
.form-row:last-child { border-bottom: none; }
.label { width: 160rpx; font-size: 28rpx; color: #333; }
.input { flex: 1; font-size: 28rpx; }
.picker { line-height: 1.4; color: #333; }
.save-btn {
  position: fixed; bottom: 40rpx; left: 24rpx; right: 24rpx;
  height: 88rpx; line-height: 88rpx; text-align: center;
  background: linear-gradient(135deg, $primary-color, $primary-light);
  color: #fff; border-radius: 44rpx; font-size: 30rpx; border: none;
}
</style>
