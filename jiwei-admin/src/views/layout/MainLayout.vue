<template>
  <div class="layout">
    <!-- 侧边栏 -->
    <aside class="sidebar" :class="{ collapsed }">
      <div class="brand">
        <div class="brand-mark">吉</div>
        <div v-show="!collapsed" class="brand-text">
          <div class="brand-name">吉味生活</div>
          <div class="brand-sub">JiweiLife Admin</div>
        </div>
      </div>

      <nav class="menu">
        <div v-for="group in menuGroups" :key="group.title" class="menu-group">
          <div v-show="!collapsed" class="group-title">{{ group.title }}</div>
          <router-link
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            class="menu-item"
            :class="{ active: route.path === item.path }"
            :title="item.title"
          >
            <el-icon :size="18"><component :is="item.icon" /></el-icon>
            <span v-show="!collapsed" class="label">{{ item.title }}</span>
          </router-link>
        </div>
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
          <el-popover placement="bottom" :width="220" trigger="click">
            <template #reference>
              <button class="icon-btn" type="button" title="通知">
                <el-badge is-dot>
                  <el-icon :size="18"><Bell /></el-icon>
                </el-badge>
              </button>
            </template>
            <div class="notice-empty">暂无新通知</div>
          </el-popover>

          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user">
              <div class="avatar">{{ avatarChar }}</div>
              <span class="user-name">{{ userStore.name || '管理员' }}</span>
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
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const collapsed = ref(localStorage.getItem('sidebar-collapsed') === '1')
const avatarChar = computed(() => (userStore.name || '管').slice(0, 1))

const menuGroups = [
  {
    title: '概览',
    items: [{ path: '/dashboard', title: '工作台', icon: 'Monitor' }]
  },
  {
    title: '交易',
    items: [{ path: '/order', title: '订单管理', icon: 'Document' }]
  },
  {
    title: '商户',
    items: [
      { path: '/merchant', title: '商户管理', icon: 'Shop' },
      { path: '/category', title: '分类管理', icon: 'Menu' },
      { path: '/dish', title: '菜品管理', icon: 'Food' },
      { path: '/setmeal', title: '套餐管理', icon: 'SetUp' }
    ]
  },
  {
    title: '营销',
    items: [
      { path: '/coupon', title: '优惠券管理', icon: 'Ticket' },
      { path: '/seckill', title: '秒杀管理', icon: 'Timer' }
    ]
  },
  {
    title: '社区',
    items: [{ path: '/content', title: '内容审核', icon: 'Checked' }]
  },
  {
    title: '系统',
    items: [
      { path: '/statistics', title: '数据统计', icon: 'DataAnalysis' },
      { path: '/employee', title: '员工管理', icon: 'UserFilled' }
    ]
  },
  {
    title: '智能',
    items: [{ path: '/ai-assistant', title: 'AI 智能助手', icon: 'MagicStick' }]
  }
]

function toggleCollapse() {
  collapsed.value = !collapsed.value
  localStorage.setItem('sidebar-collapsed', collapsed.value ? '1' : '0')
}

async function handleCommand(cmd: string) {
  if (cmd === 'logout') {
    await userStore.logout()
    ElMessage.success('已退出登录')
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
  border-right: 1px solid var(--jiwei-border);
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
  border-bottom: 1px solid var(--jiwei-border);
  flex-shrink: 0;
}
.brand-mark {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  border-radius: 10px;
  background: radial-gradient(circle at 32% 30%, var(--jiwei-glow), var(--jiwei-primary));
  color: #fff;
  font-weight: 700;
  font-size: 17px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 6px rgba(255, 122, 61, 0.32);
}
.brand-text {
  line-height: 1.25;
  overflow: hidden;
  white-space: nowrap;
}
.brand-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--jiwei-text-primary);
}
.brand-sub {
  font-size: 11px;
  color: var(--jiwei-text-secondary);
}

.menu {
  flex: 1;
  overflow-y: auto;
  padding: 8px 10px;
}
.menu-group {
  margin-bottom: 6px;
}
.group-title {
  padding: 14px 12px 6px;
  font-size: 11px;
  letter-spacing: 0.06em;
  color: var(--jiwei-text-placeholder);
}
.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 40px;
  padding: 0 12px;
  margin-bottom: 2px;
  border-radius: 10px;
  color: var(--jiwei-text-regular);
  text-decoration: none;
  font-size: 14px;
  transition: background 0.15s, color 0.15s;
}
.menu-item .el-icon {
  color: var(--jiwei-text-secondary);
  transition: color 0.15s;
}
.menu-item:hover {
  background: #f7eee4;
  color: var(--jiwei-text-primary);
}
.menu-item:hover .el-icon {
  color: var(--jiwei-primary);
}
.menu-item.active {
  background: var(--jiwei-primary-light);
  color: var(--jiwei-primary-active);
  font-weight: 600;
}
.menu-item.active .el-icon {
  color: var(--jiwei-primary);
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
  border-top: 1px solid var(--jiwei-border);
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
  color: var(--jiwei-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.collapse-btn:hover {
  background: #f7eee4;
  color: var(--jiwei-primary);
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
.notice-empty {
  color: var(--jiwei-text-secondary);
  font-size: 13px;
  text-align: center;
  padding: 12px 0;
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
  background: radial-gradient(circle at 32% 30%, var(--jiwei-glow), var(--jiwei-primary));
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
