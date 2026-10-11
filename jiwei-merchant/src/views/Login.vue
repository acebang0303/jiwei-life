<template>
  <div class="login">
    <!-- 左侧品牌区：灶火 -->
    <div class="login-hero">
      <div class="hero-flame"></div>
      <div class="hero-content">
        <div class="hero-brand">
          <div class="hero-mark">灶</div>
          <span class="hero-name">吉味生活</span>
        </div>
        <h1 class="hero-title">掌控火候，经营有道</h1>
        <p class="hero-sub">JiweiLife · 吉林本地生活服务平台 · 商家经营中心</p>
        <ul class="hero-points">
          <li><span class="flame-dot"></span>订单 · 菜品 · 门店，一站式经营</li>
          <li><span class="flame-dot"></span>数据洞察，掌握每一道菜的火候</li>
          <li><span class="flame-dot"></span>AI 经营助手，为生意添柴加火</li>
        </ul>
      </div>
    </div>

    <!-- 右侧登录表单 -->
    <div class="login-panel">
      <div class="form-wrap">
        <h2 class="form-title">欢迎回来</h2>
        <p class="form-sub">登录商家经营中心</p>
        <el-form :model="form" label-position="top">
          <el-form-item label="用户名">
            <el-input v-model="form.username" placeholder="请输入用户名" size="large" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" placeholder="请输入密码" size="large" show-password @keyup.enter="login" />
          </el-form-item>
          <el-form-item class="submit-item">
            <el-button type="primary" size="large" :loading="loading" block @click="login">登 录</el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '@/api/http'

const router = useRouter()
const loading = ref(false)
const form = reactive({ username: 'merchant01', password: '123456' })

async function login() {
  loading.value = true
  try {
    const res = await http.post('/employee/login', form)
    localStorage.setItem('token', res.data.token)
    localStorage.setItem('userName', res.data.name)
    localStorage.setItem('empId', res.data.id)
    ElMessage.success('登录成功')
    router.push('/')
  } catch { /* handled by interceptor */ }
  finally { loading.value = false }
}
</script>

<style scoped>
.login {
  display: flex;
  height: 100vh;
}

/* ---------- 左侧品牌区：灶火余烬 ---------- */
.login-hero {
  flex: 1.1;
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(circle at 24% 20%, #ffb27d 0%, rgba(255, 178, 125, 0) 46%),
    linear-gradient(150deg, #1f1b18 0%, #3a2a1d 46%, #f06b18 100%);
}
.hero-flame {
  position: absolute;
  right: -90px;
  bottom: -90px;
  width: 360px;
  height: 360px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 154, 90, 0.85), rgba(255, 154, 90, 0) 70%);
  animation: flame-breathe 6s ease-in-out infinite;
}
.hero-content {
  position: relative;
  z-index: 1;
  max-width: 420px;
  padding: 40px;
}
.hero-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 40px;
}
.hero-mark {
  width: 44px;
  height: 44px;
  border-radius: 13px;
  background: linear-gradient(160deg, var(--jiwei-glow), var(--jiwei-primary));
  color: #fff;
  font-weight: 700;
  font-size: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 14px rgba(240, 107, 24, 0.45);
}
.hero-name {
  font-size: 20px;
  font-weight: 700;
  color: #ffe9d6;
}
.hero-title {
  font-size: 34px;
  font-weight: 700;
  line-height: 1.3;
  color: #fff;
  margin: 0 0 16px;
}
.hero-sub {
  font-size: 14px;
  color: #f5cba6;
  margin: 0 0 32px;
  line-height: 1.6;
}
.hero-points {
  list-style: none;
  padding: 0;
  margin: 0;
}
.hero-points li {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: #fde3c9;
  margin-bottom: 14px;
}
.hero-points .flame-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: linear-gradient(160deg, var(--jiwei-glow), var(--jiwei-primary));
  box-shadow: 0 1px 4px rgba(240, 107, 24, 0.6);
  flex-shrink: 0;
}

/* ---------- 右侧登录表单 ---------- */
.login-panel {
  flex: 1;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}
.form-wrap {
  width: 360px;
}
.form-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--jiwei-text-primary);
  margin: 0 0 8px;
}
.form-sub {
  font-size: 14px;
  color: var(--jiwei-text-secondary);
  margin: 0 0 32px;
}
.submit-item {
  margin-top: 8px;
}

@keyframes flame-breathe {
  0%,
  100% {
    transform: scale(1);
    opacity: 0.85;
  }
  50% {
    transform: scale(1.1);
    opacity: 1;
  }
}

@media (max-width: 860px) {
  .login-hero {
    display: none;
  }
}
</style>
