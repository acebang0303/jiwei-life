<template>
  <el-card>
    <template #header>
      <div style="display:flex;align-items:center;justify-content:space-between">
        <span>🤖 AI 经营助手</span>
        <div style="display:flex;gap:8px;align-items:center">
          <el-select v-model="model" size="small" style="width:120px" @change="saveModel">
            <el-option label="DeepSeek" value="deepseek" />
            <el-option label="Kimi" value="kimi" />
            <el-option label="通义千问" value="qwen" />
          </el-select>
          <el-button size="small" text @click="newConversation">新会话</el-button>
          <el-dropdown trigger="click" @command="switchConversation">
            <el-button size="small" text>历史会话 ▾</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="c in conversations" :key="c._id" :command="c">
                  <div style="display:flex;justify-content:space-between;align-items:center;gap:12px">
                    <span>{{ c.summary || '未命名会话' }}</span>
                    <span style="color:var(--el-color-danger);font-size:12px" @click.stop="removeConversation(c)">删除</span>
                  </div>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </template>
    <div class="chat-container">
      <div class="chat-messages" ref="chatRef">
        <div v-for="(msg, i) in messages" :key="i" :class="['msg', msg.role === 'user' ? 'msg-user' : 'msg-ai']">
          <div class="msg-content">
            <span v-if="msg.role==='ai'">🤖 </span>{{ msg.content }}
            <div v-if="msg.tools && msg.tools.length" class="tool-list">
              <el-tag v-for="t in msg.tools" :key="t" size="small" type="warning">🔧 {{ t }}</el-tag>
            </div>
          </div>
        </div>
        <div v-if="loading" class="msg msg-ai">
          <div class="msg-content">🤖 正在思考{{ currentTool ? ' → 调用: ' + currentTool : '...' }}</div>
        </div>
      </div>
      <div class="chat-input">
        <el-input v-model="input" placeholder="输入问题，如：上周什么菜卖得最好？帮我分析一下差评原因？" size="large" @keyup.enter="send">
          <template #append><el-button :loading="loading" @click="send">发送</el-button></template>
        </el-input>
        <div class="quick-questions">
          <el-tag v-for="q in quickQuestions" :key="q" style="cursor:pointer;margin:4px" @click="input=q;send()">{{ q }}</el-tag>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, reactive, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const chatRef = ref<HTMLDivElement>()
const input = ref('')
const loading = ref(false)
const model = ref(localStorage.getItem('aiModel') || 'deepseek')
const conversationId = ref('')
const currentTool = ref('')
const conversations = ref<any[]>([])
const messages = reactive<{ role: string; content: string; tools: string[] }[]>([
  { role: 'ai', content: '你好！我是你的AI经营助手。你可以问我任何关于店铺经营的问题，比如销售数据、菜品分析、运营建议。', tools: [] }
])
const quickQuestions = ['上周什么菜卖得最好？', '分析一下最近的差评原因', '帮我生成本周促销文案', '这个月的营收趋势怎么样？']

const saveModel = () => localStorage.setItem('aiModel', model.value)

const loadConversations = async () => {
  const empId = localStorage.getItem('empId')
  if (!empId) { conversations.value = []; return }
  const res = await fetch(`/ai/conversation/list?userId=${empId}&agentType=MERCHANT`)
  if (!res.ok) return
  const list = await res.json()
  conversations.value = Array.isArray(list) ? list : []
}

const newConversation = () => {
  conversationId.value = ''
  messages.splice(0, messages.length, { role: 'ai', content: '新会话已开始，请问你想了解什么？', tools: [] })
}

const switchConversation = async (c: any) => {
  const prevId = conversationId.value
  conversationId.value = c._id
  const res = await fetch(`/ai/conversation/${c._id}`)
  if (!res.ok) { conversationId.value = prevId; return }
  const conv = await res.json()
  const msgs = (conv?.messages || []).map((m: any) => ({
    role: m.role === 'assistant' ? 'ai' : 'user',
    content: m.content || '',
    tools: (m.toolCalls || []).map((t: any) => t.name)
  }))
  messages.splice(0, messages.length, ...(msgs.length ? msgs : [{ role: 'ai', content: '空会话', tools: [] }]))
}

const removeConversation = async (c: any) => {
  const res = await fetch(`/ai/conversation/${c._id}`, { method: 'DELETE' })
  if (!res.ok) return
  if (conversationId.value === c._id) conversationId.value = ''
  conversations.value = conversations.value.filter((x: any) => x._id !== c._id)
  ElMessage.success('已删除')
}

async function send() {
  if (!input.value.trim() || loading.value) return
  const question = input.value.trim()
  messages.push({ role: 'user', content: question, tools: [] })
  input.value = ''
  loading.value = true
  await nextTick(); scrollToBottom()

  const empId = localStorage.getItem('empId')
  if (!empId) {
    messages.push({ role: 'ai', content: '未获取到商家身份，请重新登录。', tools: [] })
    loading.value = false
    return
  }

  const aiMsg = reactive<{ role: string; content: string; tools: string[] }>({ role: 'ai', content: '', tools: [] })
  messages.push(aiMsg)
  const tools = new Set<string>()

  const url = `/ai/merchant/stream?question=${encodeURIComponent(question)}&empId=${empId}&model=${model.value}${conversationId.value ? '&conversationId=' + conversationId.value : ''}`
  const es = new EventSource(url)
  let finished = false

  es.addEventListener('message', (e) => {
    try {
      const data = JSON.parse(e.data)
      if (data.type === 'done') {
        finished = true
        if (data.content) conversationId.value = data.content
        currentTool.value = ''
        es.close()
        loading.value = false
        if (!conversationId.value && aiMsg.content) loadConversations()
        nextTick(() => scrollToBottom())
        return
      }
      if (data.type === 'message' && data.content) {
        aiMsg.content += data.content
        nextTick(() => scrollToBottom())
      } else if (data.type === 'action') {
        const parts = (data.content || '').trim().split(/\s+/)
        const name = parts.length > 1 ? parts[1] : (parts[0] || '')
        if (name) {
          tools.add(name)
          aiMsg.tools = Array.from(tools)
          currentTool.value = name
        }
      } else if (data.type === 'error') {
        aiMsg.content = (aiMsg.content || '') + (data.content || '出错了')
      }
    } catch { /* 忽略无法解析的帧 */ }
  })

  es.addEventListener('done', () => {
    finished = true
    currentTool.value = ''
    es.close()
    loading.value = false
    if (!conversationId.value && aiMsg.content) loadConversations()
    nextTick(() => scrollToBottom())
  })

  es.onerror = () => {
    es.close()
    if (!finished && !aiMsg.content) aiMsg.content = '连接中断，请稍后重试。'
    loading.value = false
    nextTick(() => scrollToBottom())
  }
}

function scrollToBottom() {
  if (chatRef.value) chatRef.value.scrollTop = chatRef.value.scrollHeight
}

onMounted(loadConversations)
</script>

<style scoped>
.chat-container { display: flex; flex-direction: column; height: 500px; }
.chat-messages { flex: 1; overflow-y: auto; padding: 16px; background: var(--jiwei-bg-page); border-radius: 8px; margin-bottom: 12px; }
.msg { margin-bottom: 12px; display: flex; }
.msg-user { justify-content: flex-end; }
.msg-content { max-width: 75%; padding: 10px 16px; border-radius: 12px; font-size: 14px; line-height: 1.6; white-space: pre-wrap; }
.msg-user .msg-content { background: var(--jiwei-primary); color: #fff; border-bottom-right-radius: 4px; }
.msg-ai .msg-content { background: #fff; border: 1px solid var(--jiwei-border); border-bottom-left-radius: 4px; }
.quick-questions { margin-top: 8px; }
.tool-list { margin-top: 8px; display: flex; gap: 6px; flex-wrap: wrap; }
</style>
