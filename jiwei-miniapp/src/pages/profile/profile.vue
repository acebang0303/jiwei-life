<template>
  <view class="profile-page">
    <!-- 用户头部 -->
    <view class="user-header" @click="goLogin" v-if="!userStore.isLogin">
      <image class="avatar" :src="defaultAvatar" mode="aspectFill" />
      <view class="user-text">
        <text class="login-hint">点击登录</text>
        <text class="login-sub">登录后享受更多服务</text>
      </view>
      <text class="arrow">→</text>
    </view>

    <view class="user-header" v-else @click="openEdit">
      <image class="avatar" :src="userInfo.avatar || defaultAvatar" mode="aspectFill" />
      <view class="user-text">
        <text class="nickname">{{ userInfo.nickname || '微信用户' }}</text>
        <text class="level">Lv.{{ userInfo.level || 1 }} · 点击编辑资料</text>
      </view>
      <text class="arrow">→</text>
    </view>

    <!-- 数据统计 -->
    <view class="stats">
      <view class="stat-item" @click="goOrder">
        <text class="stat-num">{{ stats.orderCount || 0 }}</text>
        <text class="stat-label">订单</text>
      </view>
      <view class="stat-item">
        <text class="stat-num">{{ stats.collectCount || 0 }}</text>
        <text class="stat-label">收藏</text>
      </view>
      <view class="stat-item" @click="goFollowing">
        <text class="stat-num">{{ stats.followCount || 0 }}</text>
        <text class="stat-label">关注</text>
      </view>
      <view class="stat-item">
        <text class="stat-num">{{ stats.fansCount || 0 }}</text>
        <text class="stat-label">粉丝</text>
      </view>
    </view>

    <!-- 功能菜单 -->
    <view class="menu-section">
      <view class="menu-item" @click="goOrder">
        <text>📋 我的订单</text>
        <text class="menu-arrow">→</text>
      </view>
      <view class="menu-item" @click="goCoupon">
        <text>🎫 优惠券</text>
        <text class="menu-arrow">→</text>
      </view>
      <view class="menu-item" @click="doSign">
        <text>📅 每日签到</text>
        <text class="menu-val">{{ signedToday ? '今日已签到 +5成长值' : '签到领积分' }}</text>
      </view>
      <view class="menu-item" @click="goMyNotes">
        <text>📝 我的笔记</text>
        <text class="menu-arrow">→</text>
      </view>
      <view class="menu-item" @click="goAddress">
        <text>📍 收货地址</text>
        <text class="menu-arrow">→</text>
      </view>
      <view class="menu-item" @click="goAiChat">
        <text>🤖 AI客服</text>
        <text class="menu-arrow">→</text>
      </view>
    </view>

    <!-- 成长值卡片 -->
    <view class="growth-card" v-if="userStore.isLogin">
      <text class="growth-label">成长值</text>
      <text class="growth-num">{{ growth }}</text>
      <text class="growth-hint">签到、下单、发布笔记都可获得成长值</text>
    </view>

    <!-- 退出登录 -->
    <button class="logout-btn" v-if="userStore.isLogin" @click="logout">退出登录</button>

    <!-- 编辑资料弹窗 -->
    <view class="edit-mask" v-if="showEdit" @click="showEdit = false">
      <view class="edit-panel" @click.stop>
        <text class="edit-title">编辑资料</text>
        <button class="avatar-btn" open-type="chooseAvatar" @chooseavatar="onChooseAvatar">
          <image class="edit-avatar" :src="editForm.avatar || defaultAvatar" mode="aspectFill" />
          <text class="avatar-hint">点击更换头像</text>
        </button>
        <input class="nickname-input" v-model="editForm.nickname" placeholder="请输入昵称" />
        <view class="edit-btns">
          <button class="btn cancel" @click="showEdit = false">取消</button>
          <button class="btn save" @click="saveProfile">保存</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useUserStore } from '@/store/user.js'
import { signToday, getSignToday, getProfile, updateProfile, getProfileStats, getGrowth } from '@/api/index.js'

const userStore = useUserStore()
const defaultAvatar = '/static/icons/profile.png'
const stats = ref({})
const growth = ref(0)
const signedToday = ref(false)
const showEdit = ref(false)
const editForm = ref({ nickname: '', avatar: '' })
const userInfo = ref({})

const goLogin = () => uni.navigateTo({ url: '/pages/login/login' })
const goOrder = () => uni.navigateTo({ url: '/pages/order/order' })
const goCoupon = () => uni.navigateTo({ url: '/pages/coupon/coupon' })
const goMyNotes = () => uni.navigateTo({ url: '/pages/note/my' })
const goAddress = () => uni.navigateTo({ url: '/pages/address/address' })
const goAiChat = () => uni.navigateTo({ url: '/pages/aichat/aichat' })

// 关注列表页：复用笔记页热门？直接展示我的关注用户弹窗，简化用 toast 说明
const goFollowing = () => {
  if (!userStore.isLogin) { goLogin(); return }
  uni.showModal({
    title: '我关注的人',
    content: '关注功能已与笔记作者联动，在笔记页关注作者后可在下方查看。',
    showCancel: false,
    confirmText: '知道了'
  })
}

const openEdit = () => {
  editForm.value = { nickname: userInfo.value.nickname || '', avatar: userInfo.value.avatar || '' }
  showEdit.value = true
}

const onChooseAvatar = (e) => {
  const temp = e.detail.avatarUrl
  if (!temp) return
  // 上传到 OSS 或本地存储；开发环境直接保存临时路径（头像为微信临时文件，保存到后端不可行）
  // 用 getFileSystemManager 转存到本地持久化目录
  const fs = uni.getFileSystemManager()
  try {
    const saved = `${uni.env.USER_DATA_PATH}/avatar-${Date.now()}.png`
    fs.copyFileSync(temp, saved)
    editForm.value.avatar = saved
  } catch (err) {
    editForm.value.avatar = temp
  }
}

const saveProfile = async () => {
  if (!editForm.value.nickname.trim()) {
    uni.showToast({ title: '请输入昵称', icon: 'none' })
    return
  }
  try {
    await updateProfile({ nickname: editForm.value.nickname.trim(), avatar: editForm.value.avatar })
    userInfo.value = { ...userInfo.value, nickname: editForm.value.nickname.trim(), avatar: editForm.value.avatar }
    userStore.setLogin(userStore.token, userInfo.value)
    showEdit.value = false
    uni.showToast({ title: '保存成功', icon: 'success' })
  } catch (e) {
    uni.showToast({ title: e.message || '保存失败', icon: 'none' })
  }
}

const doSign = async () => {
  if (!userStore.isLogin) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    goLogin()
    return
  }
  if (signedToday.value) {
    uni.showToast({ title: '今日已签到，明天再来吧', icon: 'none' })
    return
  }
  try {
    await signToday()
    signedToday.value = true
    loadGrowth()
    uni.showToast({ title: '签到成功！+5成长值', icon: 'success' })
  } catch (e) {
    if (e.message && e.message.includes('已签到')) {
      signedToday.value = true
    }
    uni.showToast({ title: e.message || '签到失败', icon: 'none' })
  }
}

const loadProfile = async () => {
  try {
    const p = await getProfile()
    if (p) {
      userInfo.value = { ...userInfo.value, ...p }
      userStore.setLogin(userStore.token, userInfo.value)
    }
  } catch (e) { console.log('加载资料失败', e) }
}

const loadStats = async () => {
  try {
    stats.value = await getProfileStats() || {}
  } catch (e) { console.log('加载统计失败', e) }
}

const loadGrowth = async () => {
  try {
    const g = await getGrowth()
    growth.value = g?.growthValue || 0
  } catch (e) { console.log('加载成长值失败', e) }
}

const logout = () => {
  uni.showModal({
    title: '退出登录',
    content: '确定要退出登录吗？',
    success: (res) => {
      if (res.confirm) {
        userStore.logout()
        userInfo.value = {}
        stats.value = {}
        uni.showToast({ title: '已退出登录', icon: 'success' })
      }
    }
  })
}

onShow(async () => {
  if (userStore.isLogin) {
    userInfo.value = userStore.userInfo || {}
    loadProfile()
    loadStats()
    loadGrowth()
    try {
      const today = await getSignToday()
      signedToday.value = today?.signed || false
    } catch (e) { /* 忽略 */ }
  }
})
</script>

<style lang="scss" scoped>
.profile-page { min-height: 100vh; background: $bg-color; padding-bottom: 80rpx; }
.user-header {
  display: flex; align-items: center; padding: 48rpx 32rpx;
  background: linear-gradient(135deg, $primary-color, $primary-light);
}
.avatar { width: 100rpx; height: 100rpx; border-radius: 50%; border: 4rpx solid rgba(255,255,255,0.3); margin-right: 20rpx; background: #f0f0f0; }
.user-text { flex: 1; }
.login-hint { color: #fff; font-size: 36rpx; font-weight: bold; display: block; }
.login-sub { color: rgba(255,255,255,0.8); font-size: 24rpx; }
.nickname { color: #fff; font-size: 34rpx; font-weight: bold; display: block; }
.level { color: rgba(255,255,255,0.8); font-size: 24rpx; }
.arrow { color: rgba(255,255,255,0.6); font-size: 36rpx; }
.stats {
  display: flex; background: #fff; margin: -20rpx 24rpx 20rpx;
  border-radius: 16rpx; padding: 24rpx 0; box-shadow: $shadow;
}
.stat-item { flex: 1; text-align: center; }
.stat-num { font-size: 36rpx; font-weight: bold; color: #333; display: block; }
.stat-label { font-size: 22rpx; color: #999; margin-top: 4rpx; }
.menu-section { background: #fff; margin: 0 24rpx 16rpx; border-radius: 16rpx; overflow: hidden; }
.menu-item { display: flex; justify-content: space-between; align-items: center; padding: 28rpx 24rpx; border-bottom: 1rpx solid #f5f5f5; font-size: 28rpx; }
.menu-arrow { color: #ccc; }
.menu-val { color: $primary-color; font-size: 26rpx; }
.growth-card {
  background: #fff; margin: 0 24rpx 16rpx; border-radius: 16rpx; padding: 24rpx;
  display: flex; flex-direction: column; align-items: center;
}
.growth-label { font-size: 24rpx; color: #999; }
.growth-num { font-size: 48rpx; font-weight: bold; color: $primary-color; margin: 8rpx 0; }
.growth-hint { font-size: 22rpx; color: #ccc; }
.logout-btn { width: calc(100% - 48rpx); height: 88rpx; line-height: 88rpx; background: #fff; color: $error-color; border-radius: 16rpx; font-size: 28rpx; margin: 40rpx 24rpx; border: none; }

/* 编辑弹窗 */
.edit-mask {
  position: fixed; inset: 0; background: rgba(0,0,0,0.5);
  display: flex; align-items: center; justify-content: center; z-index: 999;
}
.edit-panel {
  width: 600rpx; background: #fff; border-radius: 20rpx; padding: 40rpx;
}
.edit-title { font-size: 32rpx; font-weight: bold; text-align: center; display: block; margin-bottom: 32rpx; }
.avatar-btn {
  width: 160rpx; height: 160rpx; border-radius: 50%; padding: 0; margin: 0 auto 24rpx;
  background: none; display: block; position: relative;
}
.edit-avatar { width: 160rpx; height: 160rpx; border-radius: 50%; background: #f0f0f0; }
.avatar-hint {
  position: absolute; bottom: -28rpx; left: 0; right: 0;
  font-size: 22rpx; color: #999; text-align: center;
}
.nickname-input {
  height: 80rpx; background: #f5f5f5; border-radius: 12rpx;
  padding: 0 24rpx; font-size: 28rpx; margin-top: 40rpx;
}
.edit-btns { display: flex; gap: 24rpx; margin-top: 32rpx; }
.btn { flex: 1; height: 72rpx; line-height: 72rpx; border-radius: 36rpx; font-size: 28rpx; border: none; }
.btn.cancel { background: #f5f5f5; color: #666; }
.btn.save { background: $primary-color; color: #fff; }
</style>
