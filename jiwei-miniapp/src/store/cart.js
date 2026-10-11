import { defineStore } from 'pinia'
import { getCartList, addToCart, updateCartNum, deleteCartItem, clearCart } from '@/api/index.js'

export const useCartStore = defineStore('cart', {
  state: () => ({
    items: [],
    totalPrice: 0,
    selectedMerchantId: null
  }),

  actions: {
    async fetchCart() {
      try {
        const items = await getCartList()
        this.items = items || []
        this.calcTotal()
      } catch (e) {
        console.error('获取购物车失败', e)
      }
    },

    async add(item) {
      await addToCart(item)
      await this.fetchCart()
    },

    async updateNum(field, number) {
      if (number <= 0) {
        await deleteCartItem(field)
      } else {
        await updateCartNum(field, number)
      }
      await this.fetchCart()
    },

    async remove(field) {
      await deleteCartItem(field)
      await this.fetchCart()
    },

    async clear() {
      await clearCart()
      this.items = []
      this.totalPrice = 0
      this.selectedMerchantId = null
    },

    calcTotal() {
      this.totalPrice = this.items.reduce((sum, item) => {
        return sum + (item.amount || 0) * (item.number || 0)
      }, 0)
    }
  }
})
