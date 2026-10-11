<template>
  <view class="app">
    <slot />
  </view>
</template>

<script setup>
import { onLaunch, onShow } from '@dcloudio/uni-app'

// 从分享卡片进入时，query.templateId 写入 storage 供 coupon 页定位
const stashTemplateId = (options) => {
  const tid = options?.query?.templateId
  if (tid) {
    uni.setStorageSync('pendingCouponTemplateId', Number(tid))
  }
}

// 冷启动（小程序被分享卡片拉起）
onLaunch((options) => {
  // 登录策略：浏览类接口游客可访问（api 层 silent 模式），交易/互动操作时再引导登录
  stashTemplateId(options)
})

// 热启动（小程序已在后台，从分享卡片回到前台）
onShow((options) => {
  stashTemplateId(options)
})
</script>

<style lang="scss">
@import '@/uni.scss';

page {
  background-color: #FFF8F1;
  font-family: 'PingFang SC', 'HarmonyOS Sans SC', -apple-system, BlinkMacSystemFont, 'Helvetica Neue', Helvetica, Arial, sans-serif;
  font-size: 28rpx;
  color: #33261E;
  line-height: 1.6;
}

.app {
  min-height: 100vh;
}
</style>
