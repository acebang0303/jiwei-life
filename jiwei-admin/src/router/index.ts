import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/store/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/Login.vue'),
    meta: { title: '登录', requiresAuth: false }
  },
  {
    path: '/coupon-share',
    name: 'CouponShare',
    component: () => import('@/views/coupon/CouponShare.vue'),
    meta: { title: '优惠券领取', requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('@/views/layout/MainLayout.vue'),
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/Dashboard.vue'),
        meta: { title: '工作台', icon: 'Monitor' }
      },
      {
        path: 'order',
        name: 'Order',
        component: () => import('@/views/order/OrderList.vue'),
        meta: { title: '订单管理', icon: 'Document' }
      },
      {
        path: 'merchant',
        name: 'Merchant',
        component: () => import('@/views/merchant/MerchantList.vue'),
        meta: { title: '商户管理', icon: 'Shop' }
      },
      {
        path: 'category',
        name: 'Category',
        component: () => import('@/views/merchant/CategoryList.vue'),
        meta: { title: '分类管理', icon: 'Menu' }
      },
      {
        path: 'dish',
        name: 'Dish',
        component: () => import('@/views/merchant/DishList.vue'),
        meta: { title: '菜品管理', icon: 'Food' }
      },
      {
        path: 'setmeal',
        name: 'Setmeal',
        component: () => import('@/views/merchant/SetmealList.vue'),
        meta: { title: '套餐管理', icon: 'SetUp' }
      },
      {
        path: 'coupon',
        name: 'Coupon',
        component: () => import('@/views/market/CouponList.vue'),
        meta: { title: '优惠券管理', icon: 'Ticket' }
      },
      {
        path: 'seckill',
        name: 'Seckill',
        component: () => import('@/views/market/SeckillList.vue'),
        meta: { title: '秒杀管理', icon: 'Timer' }
      },
      {
        path: 'content',
        name: 'Content',
        component: () => import('@/views/social/AuditList.vue'),
        meta: { title: '内容审核', icon: 'Checked' }
      },
      {
        path: 'statistics',
        name: 'Statistics',
        component: () => import('@/views/statistics/DataDashboard.vue'),
        meta: { title: '数据统计', icon: 'DataAnalysis' }
      },
      {
        path: 'employee',
        name: 'Employee',
        component: () => import('@/views/employee/EmployeeList.vue'),
        meta: { title: '员工管理', icon: 'UserFilled' }
      },
      {
        path: 'ai-assistant',
        name: 'AgentAssistant',
        component: () => import('@/views/ai/AgentAssistant.vue'),
        meta: { title: 'AI 智能助手', icon: 'MagicStick' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：未登录跳转登录页
router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()
  if (to.meta.requiresAuth !== false && !userStore.token) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
  } else {
    next()
  }
})

export default router
