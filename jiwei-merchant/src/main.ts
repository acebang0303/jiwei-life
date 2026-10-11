import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './styles/theme.css'
import {
  DataAnalysis, Document, Food, Setting, ChatDotRound,
  ArrowDown, Fold, Expand, Money, Clock, CircleCheck
} from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'

const app = createApp(App)
app.use(createPinia())
app.use(router)

// 手动注册图标：菜单/卡片用 <component :is="'IconName'"> 动态引用，unplugin 无法静态分析，需全局注册
const icons: Record<string, any> = { DataAnalysis, Document, Food, Setting, ChatDotRound, ArrowDown, Fold, Expand, Money, Clock, CircleCheck }
for (const [key, component] of Object.entries(icons)) {
  app.component(key, component)
}

app.mount('#app')
