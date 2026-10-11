import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/Login.vue'), meta: { title: '登录' } },
  {
    path: '/', component: () => import('@/views/Layout.vue'), redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '经营概览' } },
      { path: 'dish', name: 'Dish', component: () => import('@/views/DishManage.vue'), meta: { title: '菜品管理' } },
      { path: 'order', name: 'Order', component: () => import('@/views/OrderManage.vue'), meta: { title: '订单处理' } },
      { path: 'assistant', name: 'Assistant', component: () => import('@/views/AiAssistant.vue'), meta: { title: 'AI经营助手' } },
      { path: 'shop', name: 'Shop', component: () => import('@/views/ShopInfo.vue'), meta: { title: '店铺设置' } }
    ]
  }
]

export default createRouter({ history: createWebHistory(), routes })
