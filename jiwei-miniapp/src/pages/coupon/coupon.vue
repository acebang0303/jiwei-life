<template>
  <view class="coupon-page">
    <view class="coupon-tabs">
      <view class="tab" :class="{ active: tab === 'receive' }" @click="switchTab('receive')">领券中心</view>
      <view class="tab" :class="{ active: tab === 'mine' }" @click="switchTab('mine')">我的券包</view>
    </view>

    <!-- 领券中心 -->
    <view class="coupon-list" v-if="tab === 'receive'">
      <view class="coupon-card" :class="{ highlight: highlightId === t.id }" v-for="t in templates" :key="t.id">
        <view class="coupon-left">
          <view class="coupon-value">
            <text class="value">{{ couponText(t) }}</text>
            <text class="condition">{{ thresholdText(t) }}</text>
          </view>
          <view class="coupon-info">
            <text class="coupon-name">{{ t.name }}</text>
            <text class="coupon-valid">领取后 {{ t.validDays }} 天内有效</text>
          </view>
        </view>
        <view class="receive-wrap">
          <text class="share-link" @click="shareTemplate(t)">分享</text>
          <text class="received-info" v-if="t.receivedCount > 0">已领 {{ t.receivedCount }}/{{ t.perUserLimit }}</text>
          <button class="receive-btn" :class="{ done: t.receivedCount >= t.perUserLimit }"
                  :disabled="t.receivedCount >= t.perUserLimit || receiving"
                  @click="receive(t)">
            {{ t.receivedCount >= t.perUserLimit ? '已领完' : '领取' }}
          </button>
        </view>
      </view>
      <view class="empty" v-if="templates.length === 0">暂无可用优惠券</view>
    </view>

    <!-- 我的券包 -->
    <view class="coupon-list" v-else>
      <view class="coupon-card mine" v-for="c in myCoupons" :key="c.id">
        <view class="coupon-left">
          <view class="coupon-value">
            <text class="value">{{ couponText(c.template) }}</text>
            <text class="condition">{{ thresholdText(c.template) }}</text>
          </view>
          <view class="coupon-info">
            <text class="coupon-name">{{ c.template?.name || '优惠券' }}</text>
            <text class="coupon-valid">有效期至 {{ formatDate(c.expireTime) }}</text>
          </view>
        </view>
        <view class="status-tag">
          {{ c.status === 0 ? '未使用' : (c.status === 1 ? '已使用' : '已过期') }}
        </view>
      </view>
      <view class="empty" v-if="myCoupons.length === 0">暂无优惠券，快去领券中心看看吧~</view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { onShow, onShareAppMessage } from '@dcloudio/uni-app'
import { getCouponTemplates, getUserCouponList, receiveCoupon, createCouponShareLink } from '@/api/index.js'
import { useUserStore } from '@/store/user.js'

const userStore = useUserStore()
const tab = ref('receive')
const templates = ref([])
const myCoupons = ref([])
const receiving = ref(false)
const highlightId = ref(null)

const switchTab = (t) => {
  tab.value = t
  if (t === 'receive') loadTemplates()
  else loadMyCoupons()
}

const loadTemplates = async () => {
  try {
    const res = await getCouponTemplates(1, 20)
    templates.value = res?.records || []
  } catch (e) { console.log('加载券模板失败', e) }
}

const loadMyCoupons = async () => {
  try {
    const res = await getUserCouponList(1, 20)
    myCoupons.value = res?.records || []
  } catch (e) { console.log('加载我的券失败', e) }
}

const receive = async (t) => {
  if (!userStore.isLogin) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  if (t.receivedCount >= t.perUserLimit) {
    uni.showToast({ title: '已达领取上限', icon: 'none' })
    return
  }
  // 防连点
  if (receiving.value) return
  receiving.value = true
  try {
    await receiveCoupon(t.id)
    uni.showToast({ title: '领取成功！', icon: 'success' })
    loadTemplates()
    loadMyCoupons()
  } catch (e) {
    uni.showToast({ title: e.message || '领取失败', icon: 'none' })
  } finally {
    receiving.value = false
  }
}

// 券面值展示：1满减 2折扣 3无门槛
const couponText = (t) => {
  if (!t) return ''
  if (t.type === 2) return Number(t.discountValue) * 10 + '折'
  return '¥' + t.discountValue
}
const thresholdText = (t) => {
  if (!t) return ''
  if (t.type === 3) return '无门槛'
  return '满' + t.threshold + '可用'
}
const formatDate = (s) => {
  if (!s) return ''
  return (s + '').substring(0, 10)
}

// 分享单张券：生成短链（复制），并支持右上角转发
const shareTemplate = async (t) => {
  if (!userStore.isLogin) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  highlightId.value = t.id
  uni.showModal({
    title: '分享优惠券',
    content: '点击「复制短链」发给好友，或点右上角···分享给微信好友',
    confirmText: '复制短链',
    success: async (r) => {
      if (!r.confirm) return
      uni.showLoading({ title: '生成中' })
      try {
        const res = await createCouponShareLink(t.id)
        uni.hideLoading()
        const code = res?.shortCode || ''
        if (code) {
          uni.setClipboardData({ data: '/s/' + code, success: () => uni.showToast({ title: '已复制短链', icon: 'success' }) })
        }
      } catch (e) {
        uni.hideLoading()
        uni.showToast({ title: e.message || '生成失败', icon: 'none' })
      }
    }
  })
}

// 消费 App 冷/热启动写入的 templateId，定位到对应券并高亮
const applyPendingTemplate = () => {
  const tid = uni.getStorageSync('pendingCouponTemplateId')
  if (!tid) return
  uni.removeStorageSync('pendingCouponTemplateId')
  highlightId.value = Number(tid)
  if (tab.value !== 'receive') switchTab('receive')
}

onMounted(loadTemplates)
onShow(applyPendingTemplate)

// 右上角转发当前高亮券
onShareAppMessage(() => ({
  title: '送你一张优惠券，快来领取',
  path: '/pages/coupon/coupon' + (highlightId.value ? '?templateId=' + highlightId.value : '')
}))
</script>

<style lang="scss" scoped>
.coupon-page { min-height: 100vh; background: $bg-color; }
.coupon-tabs { display: flex; background: #fff; border-bottom: 1rpx solid #f0f0f0; }
.tab { flex: 1; text-align: center; padding: 24rpx 0; font-size: 28rpx; color: #666; }
.tab.active { color: $primary-color; font-weight: 600; position: relative; }
.tab.active::after {
  content: ''; position: absolute; bottom: 4rpx; left: 50%;
  transform: translateX(-50%); width: 40rpx; height: 4rpx;
  background: $primary-color; border-radius: 2rpx;
}
.coupon-list { padding: 24rpx; }
.coupon-card {
  display: flex; align-items: center; justify-content: space-between;
  background: #fff; border-radius: 16rpx; padding: 24rpx;
  margin-bottom: 16rpx; box-shadow: $shadow;
  border-left: 6rpx solid $primary-color;
}
.coupon-card.mine { border-left-color: $warning-color; }
.coupon-left { display: flex; align-items: center; flex: 1; }
.coupon-value { margin-right: 20rpx; text-align: center; min-width: 140rpx; }
.value { font-size: 44rpx; font-weight: bold; color: $primary-color; display: block; }
.condition { font-size: 22rpx; color: #999; display: block; }
.coupon-info { flex: 1; }
.coupon-name { font-size: 28rpx; font-weight: 600; display: block; }
.coupon-valid { font-size: 22rpx; color: #999; margin-top: 6rpx; display: block; }
.receive-wrap { text-align: center; }
.coupon-card.highlight { border: 3rpx solid $warning-color; box-shadow: 0 4rpx 16rpx rgba(232,161,60,.3); }
.share-link { font-size: 22rpx; color: $warning-color; display: block; margin-bottom: 8rpx; text-decoration: underline; }
.received-info { font-size: 20rpx; color: #999; display: block; margin-bottom: 6rpx; }
.receive-btn {
  width: 120rpx; height: 56rpx; line-height: 56rpx;
  background: $primary-color; color: #fff; border-radius: 28rpx;
  font-size: 24rpx; border: none;
}
.receive-btn[disabled] { background: #ccc; color: #fff; }
.receive-btn.done { background: #e8e8e8; color: #999; }
.status-tag { font-size: 24rpx; color: #999; }
.empty { text-align: center; padding: 120rpx 0; color: #999; font-size: 28rpx; }
</style>
