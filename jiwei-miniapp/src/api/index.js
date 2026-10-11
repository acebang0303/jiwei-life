const BASE_URL = 'http://localhost:8080'
const AI_BASE_URL = 'http://localhost:8087'

// 请求拦截：自动携带 token；/ai 开头走 AI 服务
const request = (options) => {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync('token')
    const header = {
      'Content-Type': 'application/json'
    }
    if (token) {
      header['Authorization'] = token
    }

    uni.request({
      url: (options.url.startsWith('/ai') ? AI_BASE_URL : BASE_URL) + options.url,
      method: options.method || 'GET',
      data: options.data,
      header: header,
      success: (res) => {
        // 静默模式：游客浏览接口 401 时不强制跳登录，只拒绝本次请求
        if (res.statusCode === 401 && options.silent) {
          reject(new Error('未登录'))
          return
        }
        if (res.statusCode === 401) {
          uni.removeStorageSync('token')
          uni.removeStorageSync('userInfo')
          uni.reLaunch({ url: '/pages/login/login' })
          reject(new Error('未登录'))
          return
        }
        if (res.data.code === 1) {
          resolve(res.data.data)
        } else if (res.data.code === 0 && res.data.msg === '用户未登录') {
          uni.removeStorageSync('token')
          uni.removeStorageSync('userInfo')
          uni.reLaunch({ url: '/pages/login/login' })
          reject(new Error('未登录'))
        } else {
          reject(new Error(res.data.msg || '请求失败'))
        }
      },
      fail: (err) => {
        reject(err)
      }
    })
  })
}

// ===== 认证 =====
export const wxLogin = (code) => request({
  url: '/user/user/login',
  method: 'POST',
  data: { code }
})

// ===== 店铺 =====
export const getMerchantList = () => request({
  url: '/user/merchant/list',
  silent: true
})

export const getMerchantDetail = (id) => request({
  url: '/user/merchant/' + id,
  silent: true
})

export const getCategoryList = (merchantId, type) => request({
  url: '/user/category/list',
  data: { merchantId, type }
})

export const getDishList = (categoryId) => request({
  url: '/user/dish/list',
  data: { categoryId }
})

export const getSetmealList = (categoryId) => request({
  url: '/user/setmeal/list',
  data: { categoryId }
})

export const getMerchantDishes = (merchantId) => request({
  url: '/user/merchant/' + merchantId + '/dishes',
  silent: true
})

export const searchDish = (keyword) => request({
  url: '/user/dish/search',
  data: { keyword },
  silent: true
})

// ===== 购物车 =====
export const addToCart = (item) => request({
  url: '/user/cart/add',
  method: 'POST',
  data: item
})

export const getCartList = () => request({
  url: '/user/cart/list'
})

export const updateCartNum = (field, number) => request({
  url: '/user/cart/' + field + '/number',
  method: 'PUT',
  data: { number }
})

export const deleteCartItem = (field) => request({
  url: '/user/cart/' + field,
  method: 'DELETE'
})

export const clearCart = () => request({
  url: '/user/cart/clear',
  method: 'DELETE'
})

// ===== 订单 =====
export const submitOrder = (data) => request({
  url: '/user/order/submit',
  method: 'POST',
  data
})

export const getOrderPage = (page, pageSize, status) => {
  // undefined/null 不传 status，避免 uni.request 序列化成 status=undefined 导致后端转换报错
  const data = { page, pageSize }
  if (status !== undefined && status !== null) data.status = status
  return request({
    url: '/user/order/page',
    data
  })
}

export const getOrderDetail = (id) => request({
  url: '/user/order/' + id
})

export const cancelOrder = (id) => request({
  url: '/user/order/' + id + '/cancel',
  method: 'PUT'
})

export const payOrder = (orderId, channelCode = 'wechat') => request({
  url: '/user/order/pay',
  method: 'POST',
  data: { orderId, channelCode }
})

// 开发环境：模拟微信支付成功回调（wechat mock 渠道需回调确认才完成入账）
export const mockPayConfirm = (outOrderNo) => {
  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE_URL + '/api/mock/wechat/pay-confirm?outOrderNo=' + outOrderNo,
      method: 'POST',
      header: { 'Content-Type': 'application/json' },
      success: (res) => {
        if (res.data.code === 1) resolve(res.data.data)
        else reject(new Error(res.data.msg || '支付确认失败'))
      },
      fail: reject
    })
  })
}

export const remindOrder = (id) => request({
  url: '/user/order/' + id + '/remind',
  method: 'PUT'
})

export const repetitionOrder = (id) => request({
  url: '/user/order/' + id + '/repetition',
  method: 'POST'
})

// ===== 地址 =====
export const getAddressList = () => request({
  url: '/user/address'
})

export const addAddress = (data) => request({
  url: '/user/address',
  method: 'POST',
  data
})

export const updateAddress = (id, data) => request({
  url: '/user/address/' + id,
  method: 'PUT',
  data
})

export const deleteAddress = (id) => request({
  url: '/user/address/' + id,
  method: 'DELETE'
})

export const setDefaultAddress = (id) => request({
  url: '/user/address/' + id + '/default',
  method: 'PUT'
})

// ===== 优惠券 =====
export const getUserCouponList = (page, pageSize) => request({
  url: '/user/coupon/list',
  data: { page, pageSize }
})

export const getCouponTemplates = (page, pageSize) => request({
  url: '/user/coupon/templates',
  data: { page, pageSize }
})

export const receiveCoupon = (templateId) => request({
  url: '/user/coupon/receive/' + templateId,
  method: 'POST'
})

// 生成优惠券分享短链（需登录，分享者是小程序登录用户）
export const createCouponShareLink = (templateId) => request({
  url: '/user/coupon-share/link?templateId=' + templateId,
  method: 'POST'
})

// ===== 签到 =====
export const signToday = () => request({
  url: '/user/sign',
  method: 'POST'
})

export const getSignToday = () => request({
  url: '/user/sign/today'
})

export const getSignMonth = () => request({
  url: '/user/sign/month'
})

// ===== 笔记社区 =====
export const getNoteFeed = (page, pageSize) => request({
  url: '/user/note/feed',
  data: { page, pageSize },
  silent: true
})

export const getNoteHot = (page, pageSize) => request({
  url: '/user/note/hot',
  data: { page, pageSize },
  silent: true
})

export const getNoteDetail = (id) => request({
  url: '/user/note/' + id,
  silent: true
})

export const getMyNotes = (page, pageSize) => request({
  url: '/user/note/my',
  data: { page, pageSize }
})

export const publishNote = (data) => request({
  url: '/user/note',
  method: 'POST',
  data
})

export const likeNote = (id) => request({
  url: '/user/note/' + id + '/like',
  method: 'POST'
})

export const collectNote = (id) => request({
  url: '/user/note/' + id + '/collect',
  method: 'POST'
})

export const followUser = (userId) => request({
  url: '/user/follow/' + userId,
  method: 'POST'
})

export const getMyFollowing = () => request({
  url: '/user/follow/me'
})

export const getNoteComments = (noteId, page, pageSize) => request({
  url: '/user/comment/note/' + noteId,
  data: { page, pageSize }
})

export const addComment = (data) => request({
  url: '/user/comment',
  method: 'POST',
  data
})

// ===== 秒杀 =====
export const getSeckillList = () => request({
  url: '/user/seckill/list',
  silent: true
})

export const buySeckill = (id, dishId) => request({
  url: '/user/seckill/' + id + '/buy',
  method: 'POST',
  data: { dishId }
})

// ===== 活动 =====
export const getActivityList = () => request({
  url: '/user/activity/list',
  silent: true
})

// ===== 用户资料 =====
export const getProfile = () => request({
  url: '/user/profile'
})

export const updateProfile = (data) => request({
  url: '/user/profile',
  method: 'PUT',
  data
})

export const getProfileStats = () => request({
  url: '/user/profile/stats'
})

export const getGrowth = () => request({
  url: '/user/profile/growth'
})

// ===== 行为上报 =====
// silent: 游客浏览时上报请求 401 不应触发跳登录，静默失败即可
export const reportBehavior = (type, targetId) => request({
  url: '/user/behavior',
  method: 'POST',
  data: { type, targetId },
  silent: true
})

// ===== AI推荐 =====
export const getAiRecommend = (userId, topN = 10) => request({
  url: '/ai/recommend/dish',
  data: { userId, topN },
  silent: true
})

// ===== AI Agent 对话 =====
export const aiAgentChat = (data) => {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync('token')
    uni.request({
      url: AI_BASE_URL + '/ai/agent/chat',
      method: 'POST',
      data,
      header: { 'Content-Type': 'application/json', ...(token ? { Authorization: token } : {}) },
      success: (res) => {
        if (res.statusCode === 200) resolve(res.data)
        else reject(new Error(res.data?.msg || 'AI服务异常'))
      },
      fail: reject
    })
  })
}

export default {
  wxLogin, getMerchantList, getMerchantDetail, getCategoryList,
  getDishList, getSetmealList, getMerchantDishes, searchDish,
  addToCart, getCartList, updateCartNum, deleteCartItem, clearCart,
  submitOrder, getOrderPage, getOrderDetail, cancelOrder, payOrder, mockPayConfirm, remindOrder,
  getAddressList, addAddress, updateAddress, deleteAddress, setDefaultAddress,
  getUserCouponList, getCouponTemplates, receiveCoupon, createCouponShareLink,
  signToday, getSignToday, getSignMonth,
  getNoteFeed, getNoteHot, getNoteDetail, getMyNotes,
  publishNote, likeNote, collectNote, followUser, getMyFollowing,
  getNoteComments, addComment,
  getSeckillList, buySeckill,
  getActivityList, getProfile, updateProfile, getProfileStats, getGrowth,
  reportBehavior, getAiRecommend, aiAgentChat
}
