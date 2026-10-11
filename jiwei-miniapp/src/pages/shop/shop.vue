<template>
  <view class="shop-page">
    <!-- 店铺头部 -->
    <view class="shop-header">
      <view class="shop-header-info">
        <text class="shop-title">{{ shop.name }}</text>
        <text class="shop-desc">{{ shop.description || '品质美食' }}</text>
        <view class="shop-meta">
          <text>🕐 {{ shop.businessHours || '09:00-22:00' }}</text>
          <text class="map-link" @click.stop="openMap">📍 查看地图 →</text>
        </view>
      </view>
    </view>

    <!-- 主体：左侧分类导航 + 右侧菜品列表 -->
    <view class="menu-body">
      <!-- 左侧分类 -->
      <scroll-view scroll-y class="cate-nav" :style="{ height: scrollH + 'px' }">
        <view v-for="c in allCategories" :key="c.id"
              class="cate-item" :class="{ active: activeCate === c.id }"
              @click="switchCate(c)">
          {{ c.name }}
        </view>
      </scroll-view>

      <!-- 右侧菜品 -->
      <scroll-view scroll-y class="dish-list" :style="{ height: scrollH + 'px' }"
                   :scroll-into-view="'cate-' + activeCate">
        <view v-for="c in allCategories" :key="c.id" :id="'cate-' + c.id" class="dish-group">
          <view class="group-title">{{ c.name }}</view>

          <!-- 菜品分类 -->
          <template v-if="c.type === 1">
            <view class="dish-row" v-for="d in c.dishes" :key="d.id">
              <image class="dish-img" :src="d.image || defaultImg" mode="aspectFill" @click="showDishDetail(d)" />
              <view class="dish-content">
                <text class="dish-name" @click="showDishDetail(d)">{{ d.name }}</text>
                <text class="dish-desc">{{ d.description || '美味可口' }}</text>
                <text class="dish-sales">月售{{ d.sales || 0 }}份</text>
                <view class="dish-bottom">
                  <text class="dish-price">¥{{ d.price }}</text>
                  <view class="stepper">
                    <text v-if="dishCount(d) > 0" class="step-btn minus" @click="minusDish(d)">-</text>
                    <text v-if="dishCount(d) > 0" class="step-num">{{ dishCount(d) }}</text>
                    <text class="step-btn plus" @click="addDish(d)">+</text>
                  </view>
                </view>
              </view>
            </view>
          </template>

          <!-- 套餐分类 -->
          <template v-else>
            <view class="dish-row" v-for="s in c.setmeals" :key="s.id">
              <image class="dish-img" :src="s.image || defaultImg" mode="aspectFill" />
              <view class="dish-content">
                <text class="dish-name">{{ s.name }}</text>
                <text class="dish-desc">{{ s.description || '超值套餐' }}</text>
                <view class="dish-bottom">
                  <text class="dish-price">¥{{ s.price }}</text>
                  <view class="stepper">
                    <text v-if="setmealCount(s) > 0" class="step-btn minus" @click="minusSetmeal(s)">-</text>
                    <text v-if="setmealCount(s) > 0" class="step-num">{{ setmealCount(s) }}</text>
                    <text class="step-btn plus setmeal-plus" @click="addSetmeal(s)">+</text>
                  </view>
                </view>
              </view>
            </view>
          </template>

          <view class="empty" v-if="c.type === 1 ? !c.dishes.length : !c.setmeals.length">该分类暂无商品</view>
        </view>
      </scroll-view>
    </view>

    <!-- 底部购物车栏（常驻） -->
    <view class="cart-bar">
      <view class="cart-icon-wrap" @click="toggleCartPop">
        <text class="cart-icon">{{ cartStore.items.length > 0 ? '🛒' : '🛒' }}</text>
        <text v-if="cartStore.items.length > 0" class="cart-badge">{{ totalCount }}</text>
      </view>
      <view class="cart-total">
        <text v-if="cartStore.items.length > 0" class="cart-price">¥{{ cartStore.totalPrice.toFixed(2) }}</text>
        <text v-else class="cart-empty-text">购物车空空如也</text>
      </view>
      <button class="checkout-btn" :class="{ disabled: cartStore.items.length === 0 }"
              @click="checkout">去结算</button>
    </view>

    <!-- 规格选择弹窗 -->
    <view class="pop-mask" v-if="showFlavorPop" @click="showFlavorPop = false">
      <view class="flavor-pop" @click.stop>
        <text class="pop-title">{{ flavorDish.name }}</text>
        <scroll-view scroll-y class="flavor-list">
          <view v-for="f in (flavorDish.flavors || [])" :key="f.id" class="flavor-group">
            <text class="flavor-name">{{ f.name }}</text>
            <view class="flavor-values">
              <view v-for="v in parseFlavorValues(f.value)" :key="v"
                    class="flavor-val" :class="{ selected: selectedFlavors[f.name] === v }"
                    @click="selectedFlavors[f.name] = v">
                {{ v }}
              </view>
            </view>
          </view>
        </scroll-view>
        <view class="flavor-footer">
          <text class="flavor-price">¥{{ flavorDish.price }}</text>
          <button class="add-cart-btn" @click="confirmAddFlavorDish">加入购物车</button>
        </view>
      </view>
    </view>

    <!-- 购物车弹窗 -->
    <view class="pop-mask" v-if="showCartPop" @click="showCartPop = false">
      <view class="cart-pop" @click.stop>
        <view class="cart-pop-header">
          <text class="cart-pop-title">购物车</text>
          <text class="clear-btn" @click="clearCart">🗑 清空</text>
        </view>
        <scroll-view scroll-y class="cart-pop-list">
          <view class="cart-pop-item" v-for="item in cartStore.items" :key="item.dishId || item.setmealId">
            <view class="cart-item-info">
              <text class="cart-item-name">{{ item.name }}</text>
              <text v-if="item.dishFlavor" class="cart-item-flavor">{{ item.dishFlavor }}</text>
              <text class="cart-item-price">¥{{ item.amount }}</text>
            </view>
            <view class="stepper">
              <text class="step-btn minus" @click="minusInCart(item)">-</text>
              <text class="step-num">{{ item.number }}</text>
              <text class="step-btn plus" @click="plusInCart(item)">+</text>
            </view>
          </view>
          <view class="cart-empty" v-if="cartStore.items.length === 0">购物车是空的</view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getMerchantDetail, getMerchantDishes, reportBehavior } from '@/api/index.js'
import { useCartStore } from '@/store/cart.js'
import { useUserStore } from '@/store/user.js'

const cartStore = useCartStore()
const userStore = useUserStore()
const defaultImg = '/static/icons/dish-default.png'

const shopId = ref(0)
const shop = ref({})
const categories = ref([])
const dishes = ref([])
const setmeals = ref([])
const activeCate = ref(0)
const showFlavorPop = ref(false)
const showCartPop = ref(false)
const flavorDish = ref({})
const selectedFlavors = ref({})

// 合并分类（菜品分类 + 套餐分类），每个分类挂上自己的商品
const allCategories = computed(() => {
  return categories.value.map(c => {
    if (c.type === 2) {
      return { ...c, dishes: [], setmeals: setmeals.value.filter(s => s.categoryId === c.id) }
    }
    return { ...c, dishes: dishes.value.filter(d => d.categoryId === c.id), setmeals: [] }
  })
})

const totalCount = computed(() => cartStore.items.reduce((sum, i) => sum + (i.number || 0), 0))

const scrollH = computed(() => {
  const sys = uni.getSystemInfoSync()
  return sys.windowHeight - 320
})

const switchCate = (c) => { activeCate.value = c.id }

// 购物车里某菜品（含口味）的数量
const dishCount = (dish) => {
  const key = dish.id
  return cartStore.items
    .filter(i => i.dishId === key)
    .reduce((sum, i) => sum + (i.number || 0), 0)
}
const setmealCount = (s) => {
  const found = cartStore.items.find(i => i.setmealId === s.id)
  return found ? found.number : 0
}

const parseFlavorValues = (json) => {
  try { return typeof json === 'string' ? JSON.parse(json) : (json || []) } catch { return [] }
}

const checkLogin = () => {
  if (!userStore.isLogin) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    uni.navigateTo({ url: '/pages/login/login' })
    return false
  }
  return true
}

// 加菜：有口味先弹规格，无口味直接加
const addDish = (dish) => {
  if (!checkLogin()) return
  if (dish.flavors && dish.flavors.length > 0) {
    flavorDish.value = dish
    selectedFlavors.value = {}
    showFlavorPop.value = true
  } else {
    doAddDish(dish, '')
  }
}

const confirmAddFlavorDish = async () => {
  const dish = flavorDish.value
  const selected = []
  for (const f of (dish.flavors || [])) {
    if (selectedFlavors.value[f.name]) {
      selected.push(f.name + ':' + selectedFlavors.value[f.name])
    }
  }
  const flavor = selected.join(',')
  await doAddDish(dish, flavor)
  showFlavorPop.value = false
}

const doAddDish = async (dish, flavor) => {
  try {
    await cartStore.add({
      dishId: dish.id, merchantId: shopId.value, name: dish.name,
      image: dish.image, amount: dish.price, number: 1,
      dishFlavor: flavor
    })
  } catch (e) {
    uni.showToast({ title: e.message || '加入失败', icon: 'none' })
  }
}

const minusDish = async (dish) => {
  // 找到第一个该菜品条目减一
  const items = cartStore.items.filter(i => i.dishId === dish.id)
  const item = items[items.length - 1]
  if (!item) return
  const field = item.dishId + '_' + (item.dishFlavor || '')
  const newNum = item.number - 1
  if (newNum <= 0) await cartStore.remove(field)
  else await cartStore.updateNum(field, newNum)
}

const addSetmeal = async (s) => {
  if (!checkLogin()) return
  try {
    await cartStore.add({
      setmealId: s.id, merchantId: shopId.value, name: s.name,
      image: s.image, amount: s.price, number: 1
    })
  } catch (e) {
    uni.showToast({ title: e.message || '加入失败', icon: 'none' })
  }
}

const minusSetmeal = async (s) => {
  const item = cartStore.items.find(i => i.setmealId === s.id)
  if (!item) return
  const field = 'setmeal_' + s.id
  const newNum = item.number - 1
  if (newNum <= 0) await cartStore.remove(field)
  else await cartStore.updateNum(field, newNum)
}

const minusInCart = (item) => {
  const field = item.dishId ? (item.dishId + '_' + (item.dishFlavor || '')) : ('setmeal_' + item.setmealId)
  const newNum = item.number - 1
  if (newNum <= 0) cartStore.remove(field)
  else cartStore.updateNum(field, newNum)
}
const plusInCart = (item) => {
  const field = item.dishId ? (item.dishId + '_' + (item.dishFlavor || '')) : ('setmeal_' + item.setmealId)
  cartStore.updateNum(field, item.number + 1)
}

const clearCart = async () => {
  await cartStore.clear()
  showCartPop.value = false
}

const showDishDetail = (dish) => {
  uni.showModal({
    title: dish.name,
    content: (dish.description || '') + '\n价格：¥' + dish.price,
    confirmText: '加入购物车',
    cancelText: '关闭',
    success: (res) => { if (res.confirm) addDish(dish) }
  })
}

const toggleCartPop = () => { showCartPop.value = !showCartPop.value }

const checkout = () => {
  if (cartStore.items.length === 0) {
    uni.showToast({ title: '购物车是空的', icon: 'none' })
    return
  }
  uni.navigateTo({ url: '/pages/order/submit' })
}

const openMap = () => {
  if (!shop.value.longitude || !shop.value.latitude) {
    uni.showToast({ title: '店铺暂未上传位置信息', icon: 'none' })
    return
  }
  uni.openLocation({
    latitude: Number(shop.value.latitude),
    longitude: Number(shop.value.longitude),
    name: shop.value.name,
    address: shop.value.address,
    fail: () => uni.showToast({ title: '无法打开地图', icon: 'none' })
  })
}

onLoad((options) => {
  shopId.value = options && options.id ? Number(options.id) : 0
})

onMounted(async () => {
  if (userStore.isLogin) reportBehavior('VIEW_MERCHANT', shopId.value).catch(() => {})
  try {
    const [shopRes, menu] = await Promise.all([
      getMerchantDetail(shopId.value),
      getMerchantDishes(shopId.value)
    ])
    shop.value = shopRes
    categories.value = menu?.categories || []
    dishes.value = menu?.dishes || []
    setmeals.value = menu?.setmeals || []
    if (categories.value.length > 0) activeCate.value = categories.value[0].id
  } catch (e) {
    console.log('加载店铺失败', e)
  }
  cartStore.fetchCart()
})
</script>

<style lang="scss" scoped>
.shop-page { padding-bottom: 120rpx; background: $bg-color; min-height: 100vh; }
.shop-header {
  background: linear-gradient(135deg, $primary-color, $primary-light);
  padding: 30rpx 24rpx;
}
.shop-title { color: #fff; font-size: 40rpx; font-weight: bold; display: block; }
.shop-desc { color: rgba(255,255,255,0.85); font-size: 24rpx; display: block; margin-top: 4rpx; }
.shop-meta { display: flex; gap: 24rpx; margin-top: 10rpx; font-size: 22rpx; color: rgba(255,255,255,0.8); }
.map-link { text-decoration: underline; }

.menu-body { display: flex; }
.cate-nav { width: 180rpx; background: #f7f4f0; flex-shrink: 0; }
.cate-item {
  padding: 28rpx 16rpx; font-size: 26rpx; color: #666;
  text-align: center; border-left: 6rpx solid transparent;
}
.cate-item.active {
  background: #fff; color: $primary-color; font-weight: 600;
  border-left-color: $primary-color;
}
.dish-list { flex: 1; padding: 16rpx 20rpx; }
.group-title { font-size: 30rpx; font-weight: bold; padding: 16rpx 0 8rpx; }
.dish-row { display: flex; padding: 16rpx 0; border-bottom: 1rpx solid #f0f0f0; }
.dish-img { width: 150rpx; height: 150rpx; border-radius: 12rpx; margin-right: 16rpx; background: #f0f0f0; }
.dish-content { flex: 1; display: flex; flex-direction: column; }
.dish-name { font-size: 30rpx; font-weight: 600; }
.dish-desc { font-size: 24rpx; color: #999; margin-top: 4rpx; }
.dish-sales { font-size: 22rpx; color: #ccc; margin-top: 4rpx; }
.dish-bottom { display: flex; justify-content: space-between; align-items: center; margin-top: auto; }
.dish-price { font-size: 32rpx; font-weight: bold; color: $primary-color; }

.stepper { display: flex; align-items: center; gap: 12rpx; }
.step-btn {
  width: 44rpx; height: 44rpx; line-height: 42rpx; text-align: center;
  border-radius: 50%; font-size: 30rpx; font-weight: bold;
}
.step-btn.plus { background: $primary-color; color: #fff; }
.step-btn.minus { background: #fff; color: $primary-color; border: 1rpx solid $primary-color; }
.step-num { font-size: 28rpx; font-weight: 600; min-width: 30rpx; text-align: center; }
.empty { text-align: center; padding: 40rpx 0; color: #ccc; font-size: 24rpx; }

.cart-bar {
  position: fixed; bottom: 0; left: 0; right: 0;
  display: flex; align-items: center; padding: 16rpx 24rpx;
  background: #fff; box-shadow: 0 -2rpx 12rpx rgba(0,0,0,0.08);
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
}
.cart-icon-wrap { position: relative; margin-right: 16rpx; }
.cart-icon { font-size: 56rpx; }
.cart-badge {
  position: absolute; top: -8rpx; right: -12rpx;
  min-width: 32rpx; height: 32rpx; line-height: 32rpx;
  background: $error-color; color: #fff; font-size: 20rpx;
  border-radius: 16rpx; text-align: center; padding: 0 6rpx;
}
.cart-total { flex: 1; }
.cart-price { font-size: 36rpx; font-weight: bold; color: $primary-color; }
.cart-empty-text { font-size: 26rpx; color: #999; }
.checkout-btn {
  height: 72rpx; line-height: 72rpx; padding: 0 40rpx;
  background: linear-gradient(135deg, $primary-color, $primary-light);
  color: #fff; border-radius: 36rpx; font-size: 28rpx; border: none;
}
.checkout-btn.disabled { background: #ccc; }

.pop-mask {
  position: fixed; inset: 0; background: rgba(0,0,0,0.5);
  z-index: 999; display: flex; align-items: flex-end;
}
.flavor-pop {
  width: 100%; background: #fff; border-radius: 24rpx 24rpx 0 0;
  padding: 32rpx 24rpx; max-height: 70vh; display: flex; flex-direction: column;
}
.pop-title { font-size: 32rpx; font-weight: bold; display: block; margin-bottom: 16rpx; }
.flavor-list { max-height: 50vh; }
.flavor-group { margin-bottom: 20rpx; }
.flavor-name { font-size: 26rpx; color: #999; display: block; margin-bottom: 10rpx; }
.flavor-values { display: flex; flex-wrap: wrap; gap: 16rpx; }
.flavor-val {
  padding: 10rpx 24rpx; background: #f5f5f5; border-radius: 28rpx;
  font-size: 26rpx; color: #333;
}
.flavor-val.selected { background: $primary-color; color: #fff; }
.flavor-footer { display: flex; align-items: center; margin-top: 20rpx; }
.flavor-price { flex: 1; font-size: 32rpx; font-weight: bold; color: $primary-color; }
.add-cart-btn {
  height: 72rpx; line-height: 72rpx; padding: 0 40rpx;
  background: $primary-color; color: #fff; border-radius: 36rpx; font-size: 28rpx; border: none;
}

.cart-pop {
  width: 100%; background: #fff; border-radius: 24rpx 24rpx 0 0;
  padding: 32rpx 24rpx; max-height: 60vh; display: flex; flex-direction: column;
}
.cart-pop-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16rpx; }
.cart-pop-title { font-size: 32rpx; font-weight: bold; }
.clear-btn { font-size: 26rpx; color: $error-color; }
.cart-pop-list { max-height: 45vh; }
.cart-pop-item { display: flex; align-items: center; justify-content: space-between; padding: 16rpx 0; border-bottom: 1rpx solid #f0f0f0; }
.cart-item-info { flex: 1; }
.cart-item-name { font-size: 28rpx; display: block; }
.cart-item-flavor { font-size: 22rpx; color: #999; display: block; }
.cart-item-price { font-size: 26rpx; color: $primary-color; font-weight: 600; }
.cart-empty { text-align: center; padding: 40rpx 0; color: #ccc; font-size: 26rpx; }
</style>
