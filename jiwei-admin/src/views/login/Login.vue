<template>
  <div class="login">
    <!-- 左侧品牌区 -->
    <div class="login-hero">
      <div class="hero-sun"></div>
      <div class="hero-content">
        <div class="hero-brand">
          <div class="hero-mark">吉</div>
          <span class="hero-name">吉味生活</span>
        </div>
        <h1 class="hero-title">记录生活的味道</h1>
        <p class="hero-sub">JiweiLife · 本地生活 + 内容社区 + AI 助手 统一管理后台</p>
        <ul class="hero-points">
          <li><span class="dot"></span>笔记 · 评价 · 关注，经营内容社区</li>
          <li><span class="dot"></span>订单 · 商户 · 营销，一站式管理</li>
          <li><span class="dot"></span>数据洞察，驱动经营决策</li>
        </ul>
      </div>
    </div>

    <!-- 右侧登录表单 -->
    <div class="login-panel">
      <div class="form-wrap">
        <h2 class="form-title">欢迎回来</h2>
        <p class="form-sub">登录 JiweiLife 管理后台</p>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" placeholder="请输入用户名" size="large" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              size="large"
              show-password
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-form-item class="submit-item">
            <el-button type="primary" size="large" :loading="loading" block @click="handleLogin">
              登 录
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  username: 'admin',
  password: '123456'
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login(form)
    ElMessage.success('登录成功')
    router.push('/')
  } catch (e: any) {
    ElMessage.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login {
  display: flex;
  height: 100vh;
}

/* ---------- 左侧品牌区：黄昏暖阳 ---------- */
.login-hero {
  flex: 1.1;
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(circle at 22% 18%, #ffe4c7 0%, rgba(255, 228, 199, 0) 48%),
    linear-gradient(150deg, #fff6ec 0%, #ffe0bf 46%, #ffa86b 100%);
}
.hero-sun {
  position: absolute;
  right: -80px;
  top: -80px;
  width: 320px;
  height: 320px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 185, 138, 0.9), rgba(255, 185, 138, 0) 70%);
  animation: sun-breathe 7s ease-in-out infinite;
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
  background: radial-gradient(circle at 32% 30%, var(--jiwei-glow), var(--jiwei-primary));
  color: #fff;
  font-weight: 700;
  font-size: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(255, 122, 61, 0.35);
}
.hero-name {
  font-size: 20px;
  font-weight: 700;
  color: #6b3a1a;
}
.hero-title {
  font-size: 34px;
  font-weight: 700;
  line-height: 1.3;
  color: #5b3217;
  margin: 0 0 16px;
}
.hero-sub {
  font-size: 14px;
  color: #8a5a34;
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
  color: #7a4a22;
  margin-bottom: 14px;
}
.hero-points .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 35%, var(--jiwei-glow), var(--jiwei-primary));
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

@keyframes sun-breathe {
  0%,
  100% {
    transform: scale(1);
    opacity: 0.85;
  }
  50% {
    transform: scale(1.12);
    opacity: 1;
  }
}

@media (max-width: 860px) {
  .login-hero {
    display: none;
  }
}
</style>
