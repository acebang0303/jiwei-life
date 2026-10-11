import axios from 'axios'

// 公开接口走 /user 前缀（vite 代理 /user → 8080 不 rewrite），不注入管理端 token
export const getCouponShareInfo = (templateId: number) =>
  axios.get('/user/coupon-share/info', { params: { templateId } }).then(r => r.data?.data)

export const getCouponShareMiniCode = (templateId: number) =>
  axios.get('/user/coupon-share/minicode', { params: { templateId } }).then(r => r.data?.data)
