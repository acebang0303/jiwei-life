<template>
  <div class="layout">
    <!-- 炭黑侧边栏 -->
    <aside class="sidebar" :class="{ collapsed }">
      <div class="brand">
        <div class="brand-mark">灶</div>
        <div v-show="!collapsed" class="brand-text">
          <div class="brand-name">吉味生活</div>
          <div class="brand-sub">商家经营中心</div>
        </div>
      </div>

      <nav class="menu">
        <router-link
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          class="menu-item"
          :class="{ active: route.path === item.path }"
          :title="item.title"
        >
          <span class="flame"></span>
          <el-icon :size="18"><component :is="item.icon" /></el-icon>
          <span v-show="!collapsed" class="label">{{ item.title }}</span>
        </router-link>
      </nav>

      <div v-show="!collapsed" class="sidebar-footer">
        <button class="collapse-btn" type="button" @click="toggleCollapse">
          <el-icon :size="16"><Fold /></el-icon>
          <span>收起菜单</span>
        </button>
      </div>
    </aside>

    <!-- 主区域 -->
    <div class="main">
      <header class="topbar">
        <div class="topbar-left">
          <button class="icon-btn" type="button" :title="collapsed ? '展开菜单' : '收起菜单'" @click="toggleCollapse">
            <el-icon :size="18"><component :is="collapsed ? 'Expand' : 'Fold'" /></el-icon>
          </button>
          <h1 class="page-title">{{ route.meta.title }}</h1>
        </div>

        <div class="topbar-right">
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user">
              <div class="avatar">{{ avatarChar }}</div>
              <span class="user-name">{{ userName || '商家' }}</span>
              <el-icon :size="14"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const collapsed = ref(localStorage.getItem('sidebar-collapsed') === '1')
const userName = ref(localStorage.getItem('userName') || '')
const avatarChar = computed(() => (userName.value || '商').slice(0, 1))

const menuItems = [
  { path: '/dashboard', title: '经营概览', icon: 'DataAnalysis' },
  { path: '/order', title: '订单处理', icon: 'Document' },
  { path: '/dish', title: '菜品管理', icon: 'Food' },
  { path: '/shop', title: '店铺设置', icon: 'Setting' },
  { path: '/assistant', title: 'AI 经营助手', icon: 'ChatDotRound' }
]

function toggleCollapse() {
  collapsed.value = !collapsed.value
  localStorage.setItem('sidebar-collapsed', collapsed.value ? '1' : '0')
}

function handleCommand(cmd: string) {
  if (cmd === 'logout') {
    localStorage.clear()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ---------- 侧边栏 ---------- */
.sidebar {
  width: 224px;
  flex-shrink: 0;
  background: var(--jiwei-bg-sidebar);
  display: flex;
  flex-direction: column;
  transition: width 0.2s ease;
  overflow: hidden;
}
.sidebar.collapsed {
  width: 64px;
}

.brand {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  border-bottom: 1px solid var(--jiwei-sidebar-border);
  flex-shrink: 0;
}
.brand-mark {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  border-radius: 10px;
  background: linear-gradient(160deg, var(--jiwei-glow), var(--jiwei-primary));
  color: #fff;
  font-weight: 700;
  font-size: 17px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(240, 107, 24, 0.4);
}
.brand-text {
  line-height: 1.25;
  overflow: hidden;
  white-space: nowrap;
}
.brand-name {
  font-size: 15px;
  font-weight: 700;
  color: #fff;
}
.brand-sub {
  font-size: 11px;
  color: var(--jiwei-sidebar-text);
}

.menu {
  flex: 1;
  overflow-y: auto;
  padding: 12px 10px;
}
.menu-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 42px;
  padding: 0 12px;
  margin-bottom: 4px;
  border-radius: 10px;
  color: var(--jiwei-sidebar-text);
  text-decoration: none;
  font-size: 14px;
  transition: background 0.15s, color 0.15s;
}
.menu-item .el-icon {
  color: var(--jiwei-sidebar-text);
  transition: color 0.15s;
}
.menu-item:hover {
  background: var(--jiwei-sidebar-hover);
  color: #fff;
}
.menu-item:hover .el-icon {
  color: var(--jiwei-glow);
}
.menu-item.active {
  background: rgba(240, 107, 24, 0.16);
  color: var(--jiwei-sidebar-text-active);
  font-weight: 600;
}
.menu-item.active .el-icon {
  color: var(--jiwei-glow);
}

/* 火焰指示条 */
.flame {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 20px;
  border-radius: 2px;
  opacity: 0;
  background: linear-gradient(180deg, var(--jiwei-cream), var(--jiwei-primary));
  box-shadow: 0 1px 6px rgba(240, 107, 24, 0.5);
  transition: opacity 0.15s;
}
.menu-item.active .flame {
  opacity: 1;
}

.sidebar.collapsed .menu-item {
  justify-content: center;
  padding: 0;
}
.sidebar.collapsed .brand {
  justify-content: center;
  padding: 0;
}

.sidebar-footer {
  padding: 10px;
  border-top: 1px solid var(--jiwei-sidebar-border);
  flex-shrink: 0;
}
.collapse-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 12px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--jiwei-sidebar-text);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.collapse-btn:hover {
  background: var(--jiwei-sidebar-hover);
  color: var(--jiwei-glow);
}

/* ---------- 主区域 ---------- */
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.topbar {
  height: 60px;
  flex-shrink: 0;
  background: #fff;
  border-bottom: 1px solid var(--jiwei-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}
.topbar-left,
.topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--jiwei-text-primary);
  margin: 0;
}
.icon-btn {
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--jiwei-text-regular);
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s, color 0.15s;
}
.icon-btn:hover {
  background: #f7eee4;
  color: var(--jiwei-primary);
}
.user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background 0.15s;
}
.user:hover {
  background: #f7eee4;
}
.avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: linear-gradient(160deg, var(--jiwei-glow), var(--jiwei-primary));
  color: #fff;
  font-weight: 600;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.user-name {
  font-size: 14px;
  color: var(--jiwei-text-regular);
}

.content {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: var(--jiwei-bg-page);
}
</style>
