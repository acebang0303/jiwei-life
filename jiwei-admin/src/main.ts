import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/theme.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import {
  Checked, DataAnalysis, Document, Food, Menu,
  Monitor, SetUp, Shop, Ticket, Timer, UserFilled,
  Search, Plus, Edit, Delete, Upload,
  Bell, Fold, Expand, ArrowDown, ShoppingCart, Money, Clock, CircleCheck,
  MagicStick
} from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 按需注册图标（非全量导入，避免 ~1MB 额外体积）
const icons: Record<string, any> = { Checked, DataAnalysis, Document, Food, Menu, Monitor, SetUp, Shop, Ticket, Timer, UserFilled, Search, Plus, Edit, Delete, Upload, Bell, Fold, Expand, ArrowDown, ShoppingCart, Money, Clock, CircleCheck, MagicStick }
for (const [key, component] of Object.entries(icons)) {
  app.component(key, component)
}

app.mount('#app')
