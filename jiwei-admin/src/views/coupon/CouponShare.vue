<template>
  <div class="coupon-share-page">
    <div v-if="loading" class="center">加载中...</div>
    <div v-else-if="error" class="center">{{ error }}</div>
    <div v-else-if="template" class="coupon-card">
      <h2>{{ template.name }}</h2>
      <p class="value">{{ couponText }}</p>
      <p class="threshold">{{ thresholdText }}</p>
      <p class="valid">领取后 {{ template.validDays }} 天内有效</p>
      <img v-if="miniCode.image" :src="miniCode.image" class="mini-code" alt="小程序码" />
      <p class="hint">微信扫码 / 长按识别打开小程序即可领取</p>
      <p class="hint2">或在小程序中打开「领券中心」领取</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getCouponShareInfo, getCouponShareMiniCode } from '@/api/couponShare'
import type { CouponTemplate } from '@/types'

const route = useRoute()
const templateId = Number(route.query.templateId)
const loading = ref(true)
const error = ref('')
const template = ref<CouponTemplate | null>(null)
const miniCode = ref<{ image: string }>({ image: '' })

const couponText = computed(() => {
  const t = template.value
  if (!t) return ''
  if (t.type === 2) return (Number(t.discountValue) * 10).toFixed(1) + '折'
  return '¥' + t.discountValue
})
const thresholdText = computed(() => {
  const t = template.value
  if (!t) return ''
  if (t.type === 3) return '无门槛'
  return '满' + t.threshold + '可用'
})

onMounted(async () => {
  if (!templateId) { error.value = '链接无效'; loading.value = false; return }
  try {
    const t = await getCouponShareInfo(templateId)
    if (!t) { error.value = '优惠券不存在或活动已结束'; loading.value = false; return }
    template.value = t
    const mc = await getCouponShareMiniCode(templateId)
    if (mc) miniCode.value = mc
  } catch (e: any) {
    error.value = e?.response?.data?.msg || '券信息加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.coupon-share-page { min-height: 100vh; background: #FFF8F1; display: flex; align-items: center; justify-content: center; padding: 24px; box-sizing: border-box; }
.center { color: #999; font-size: 15px; }
.coupon-card { text-align: center; background: #fff; border: 2px dashed #FF7A3D; border-radius: 12px; padding: 32px; max-width: 420px; width: 100%; }
h2 { color: #33261E; }
.value { font-size: 42px; color: #FF7A3D; font-weight: bold; margin: 8px 0; }
.threshold, .valid { color: #999; margin: 4px 0; }
.mini-code { width: 180px; height: 180px; margin-top: 16px; }
.hint { color: #666; margin-top: 12px; font-size: 14px; }
.hint2 { color: #999; margin-top: 6px; font-size: 13px; }
</style>
