import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: uni.getStorageSync('token') || '',
    userInfo: uni.getStorageSync('userInfo') || null,
    isLogin: false
  }),

  actions: {
    setLogin(token, userInfo) {
      this.token = token
      this.userInfo = userInfo
      this.isLogin = true
      uni.setStorageSync('token', token)
      uni.setStorageSync('userInfo', userInfo)
    },

    logout() {
      this.token = ''
      this.userInfo = null
      this.isLogin = false
      uni.removeStorageSync('token')
      uni.removeStorageSync('userInfo')
    }
  }
})
