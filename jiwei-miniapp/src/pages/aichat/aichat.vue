<template>
  <view class="chat-page">
    <!-- 消息列表 -->
    <scroll-view scroll-y class="msg-area" :scroll-top="scrollTop">
      <view class="msg-row" :class="m.role === 'user' ? 'user' : 'bot'"
            v-for="m in chatMessages" :key="m.id">
        <view class="bubble" :class="m.role === 'user' ? 'user-bubble' : 'bot-bubble'">
          <text class="msg-text">{{ m.text }}</text>
          <view class="dish-results" v-if="m.dishes && m.dishes.length > 0">
            <view class="dish-result" v-for="d in m.dishes" :key="d.id" @click="goShop(d.merchantId)">
              <image class="dish-img" :src="d.image || defaultImg" mode="aspectFill" />
              <view class="dish-info">
                <text class="dish-name">{{ d.name }}</text>
                <text class="dish-shop">🏪 {{ d.merchantName }}</text>
                <text class="dish-reason">{{ d.reason }}</text>
              </view>
              <view class="dish-right">
                <text class="dish-price">¥{{ d.price }}</text>
                <text class="go-link">去看看 →</text>
              </view>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>

    <!-- 快捷问题 -->
    <scroll-view scroll-x class="quick-row" v-if="chatMessages.length <= 1">
      <text class="quick-tag" v-for="q in quickQuestions" :key="q" @click="send(q)">{{ q }}</text>
    </scroll-view>

    <!-- 输入栏 -->
    <view class="input-bar">
      <input class="chat-input" v-model="input" placeholder="问我任何美食问题..." confirm-type="send"
             @confirm="send(input)" />
      <text class="send-btn" @click="send(input)">发送</text>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { aiAgentChat } from '@/api/index.js'

const defaultImg = '/static/icons/dish-default.png'
const input = ref('')
// 单一消息数组，按 role 区分 user/assistant，保证时间顺序
const chatMessages = ref([{
  id: 0,
  role: 'assistant',
  text: '你好，我是吉味生活的 AI 美食助手 🍜\n你可以问我：\n· 想吃什么菜、什么口味\n· 推荐适合约会/聚餐/加班的餐厅\n· 直接说菜品名，我帮你找店',
  dishes: []
}])
const scrollTop = ref(0)
const loading = ref(false)
let msgId = 1

const conversationId = ref(uni.getStorageSync('aiConvId') || '')

const quickQuestions = ['适合约会的餐厅', '深夜烧烤', '加班吃什么', '川菜推荐', '家庭聚餐']

const send = async (text) => {
  const kw = (text || '').trim()
  if (!kw || loading.value) return
  input.value = ''
  loading.value = true
  // 1、追加用户消息
  chatMessages.value.push({ id: msgId++, role: 'user', text: kw, dishes: [] })
  // 2、追加 assistant 占位消息并记录其 id，返回后原地更新
  const placeholderId = msgId++
  chatMessages.value.push({ id: placeholderId, role: 'assistant', text: '🤖 正在规划中...', dishes: [] })
  scrollToBottom()
  try {
    const res = await aiAgentChat({
      agentType: 'EXPLORE',
      message: kw,
      model: 'deepseek',
      userId: uni.getStorageSync('userInfo')?.id || 1,
      conversationId: conversationId.value || null
    })
    if (res.conversationId) {
      conversationId.value = res.conversationId
      uni.setStorageSync('aiConvId', res.conversationId)
    }
    // 工具调用过程
    const toolNames = (res.events || [])
      .filter(e => e.type === 'action')
      .flatMap(e => (e.content || '').split('\n'))
      .map(line => {
        const parts = line.trim().split(/\s+/)
        return parts.length > 1 ? parts[1] : line.trim()
      })
      .filter(name => name && name !== '工具')
    const toolText = toolNames.length ? '\n\n🔧 已检索：' + [...new Set(toolNames)].join('、') : ''
    updatePlaceholder(placeholderId, res.finalAnswer + toolText)
  } catch (e) {
    updatePlaceholder(placeholderId, 'AI 服务暂不可用，请稍后再试。')
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

// 用 id 定位占位消息并原地更新内容（不新增气泡）
const updatePlaceholder = (id, text) => {
  const msg = chatMessages.value.find(m => m.id === id)
  if (msg) msg.text = text
}

const goShop = (merchantId) => {
  uni.navigateTo({ url: '/pages/shop/shop?id=' + merchantId })
}

const scrollToBottom = () => {
  setTimeout(() => { scrollTop.value = 99999 }, 100)
}
</script>

<style lang="scss" scoped>
.chat-page { min-height: 100vh; background: $bg-color; display: flex; flex-direction: column; }
.msg-area { flex: 1; padding: 24rpx; height: calc(100vh - 220rpx); }
.msg-row { display: flex; margin-bottom: 20rpx; }
.msg-row.bot { justify-content: flex-start; }
.msg-row.user { justify-content: flex-end; }
.bubble {
  max-width: 80%; padding: 20rpx 24rpx; border-radius: 16rpx; font-size: 28rpx;
}
.bot-bubble { background: #fff; border-top-left-radius: 4rpx; box-shadow: $shadow; }
.user-bubble {
  background: linear-gradient(135deg, $primary-color, $primary-light);
  color: #fff; border-top-right-radius: 4rpx;
}
.msg-text { line-height: 1.6; white-space: pre-line; }
.dish-results { margin-top: 16rpx; }
.dish-result {
  display: flex; align-items: center; padding: 16rpx 0;
  border-top: 1rpx solid #f5f5f5;
}
.dish-img { width: 100rpx; height: 100rpx; border-radius: 8rpx; margin-right: 12rpx; background: #f0f0f0; }
.dish-info { flex: 1; }
.dish-name { font-size: 28rpx; font-weight: 600; display: block; }
.dish-shop { font-size: 22rpx; color: #999; display: block; margin-top: 2rpx; }
.dish-reason {
  font-size: 20rpx; color: $warning-color; display: block; margin-top: 2rpx;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.dish-right { text-align: right; }
.dish-price { font-size: 28rpx; font-weight: bold; color: $primary-color; display: block; }
.go-link { font-size: 20rpx; color: $primary-color; display: block; margin-top: 4rpx; }
.quick-row { padding: 0 24rpx 16rpx; white-space: nowrap; }
.quick-tag {
  display: inline-block; padding: 12rpx 24rpx; background: #fff;
  border-radius: 24rpx; font-size: 24rpx; color: #666; margin-right: 12rpx;
  box-shadow: $shadow;
}
.input-bar {
  display: flex; align-items: center; padding: 16rpx 24rpx;
  background: #fff; padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
}
.chat-input {
  flex: 1; height: 72rpx; background: #f5f5f5; border-radius: 36rpx;
  padding: 0 24rpx; font-size: 28rpx;
}
.send-btn {
  margin-left: 16rpx; font-size: 28rpx; color: $primary-color; font-weight: 600;
}
</style>
