<template>
  <div class="agent-assistant">
    <el-row :gutter="16">
      <el-col :span="6">
        <el-card class="conv-card">
          <template #header>
            <div class="conv-header">
              <span>会话列表</span>
              <el-button size="small" type="primary" @click="newConversation">+ 新建</el-button>
            </div>
          </template>
          <div class="conv-list">
            <div v-for="c in conversations" :key="c._id"
                 :class="['conv-item', { active: c._id === conversationId }]"
                 @click="switchConversation(c)">
              <div class="conv-title">{{ c.summary || '未命名会话' }}</div>
              <el-button size="small" text type="danger" @click.stop="removeConversation(c)">删除</el-button>
            </div>
            <el-empty v-if="!conversations.length" description="暂无历史会话" :image-size="60" />
          </div>
        </el-card>
      </el-col>

      <el-col :span="18">
        <el-card>
          <template #header>
            <div class="chat-header">
              <span>🤖 平台运营智能助手</span>
              <el-select v-model="model" size="small" style="width: 140px" @change="saveModel">
                <el-option label="DeepSeek" value="deepseek" />
                <el-option label="Kimi" value="kimi" />
                <el-option label="通义千问" value="qwen" />
              </el-select>
            </div>
          </template>

          <div class="chat-messages" ref="chatRef">
            <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role === 'user' ? 'msg-user' : 'msg-ai']">
              <div class="msg-bubble">
                <span v-if="m.role === 'ai'">🤖 </span>{{ m.content }}
                <div v-if="m.tools && m.tools.length" class="tool-list">
                  <el-tag v-for="t in m.tools" :key="t" size="small" type="warning">🔧 {{ t }}</el-tag>
                </div>
              </div>
            </div>
            <div v-if="loading" class="msg msg-ai">
              <div class="msg-bubble">
                <span>🤖 正在思考
                  <template v-if="currentTool"> → 调用工具: {{ currentTool }}</template>
                </span>
              </div>
            </div>
          </div>

          <div class="quick-questions">
            <el-tag v-for="q in quickQuestions" :key="q" class="quick-tag" @click="send(q)">{{ q }}</el-tag>
          </div>

          <div class="chat-input">
            <el-input v-model="input" placeholder="问我任何平台运营问题..." size="large"
                      @keyup.enter="send(input)">
              <template #append>
                <el-button :loading="loading" @click="send(input)">发送</el-button>
              </template>
            </el-input>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { storeToRefs } from 'pinia'
import { useUserStore } from '@/store/user'

const { employeeId } = storeToRefs(useUserStore())
const EMP_ID = employeeId.value || '1'
const chatRef = ref<HTMLDivElement>()
const input = ref('')
const loading = ref(false)
const currentTool = ref('')
const model = ref(localStorage.getItem('aiModel') || 'deepseek')
const conversationId = ref('')
const conversations = ref<any[]>([])
const messages = reactive<{ role: string; content: string; tools: string[] }[]>([
  { role: 'ai', content: '你好！我是平台运营智能助手。可以问我经营数据、内容审核、运营制度、商户建议等问题。', tools: [] }
])

const quickQuestions = ['本周营收TOP3商户', '分析一下最近的差评原因', '入驻流程是什么？', '给我第1号商户的经营建议']

const saveModel = () => localStorage.setItem('aiModel', model.value)

const scrollToBottom = () => {
  nextTick(() => { if (chatRef.value) chatRef.value.scrollTop = chatRef.value.scrollHeight })
}

const loadConversations = async () => {
  const res = await fetch(`/ai/conversation/list?userId=${EMP_ID}&agentType=ADMIN`)
  if (!res.ok) return
  const list = await res.json()
  conversations.value = Array.isArray(list) ? list : []
}

const newConversation = () => {
  conversationId.value = ''
  messages.splice(0, messages.length, { role: 'ai', content: '新会话已开始，请问你想了解什么？', tools: [] })
  loadConversations()
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
  ElMessage.success('已删除')
  loadConversations()
}

async function send(text?: string) {
  const question = (text ?? input.value).trim()
  if (!question || loading.value) return
  input.value = ''
  messages.push({ role: 'user', content: question, tools: [] })
  loading.value = true
  scrollToBottom()

  const aiMsg = reactive<{ role: string; content: string; tools: string[] }>({ role: 'ai', content: '', tools: [] })
  messages.push(aiMsg)
  const tools = new Set<string>()

  const es = new EventSource(`/ai/admin/stream?message=${encodeURIComponent(question)}&empId=${EMP_ID}&model=${model.value}${conversationId.value ? '&conversationId=' + conversationId.value : ''}`)
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
        loadConversations()
        scrollToBottom()
        return
      }
      if (data.type === 'message' && data.content) {
        aiMsg.content += data.content
        scrollToBottom()
      } else if (data.type === 'action') {
        const parts = (data.content || '').trim().split(/\s+/)
        const name = parts.length > 1 ? parts[1] : (data.content || '').trim()
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
    if (!conversationId.value && aiMsg.content) {
      // 首轮后刷新会话列表
      loadConversations()
    }
    scrollToBottom()
  })
  es.onerror = () => {
    es.close()
    loading.value = false
    if (!finished && !aiMsg.content) aiMsg.content = '连接中断，请稍后重试。'
  }
}

onMounted(loadConversations)
</script>

<style scoped>
.agent-assistant { padding: 16px; }
.conv-header { display: flex; justify-content: space-between; align-items: center; }
.conv-list { max-height: 480px; overflow-y: auto; }
.conv-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 8px; border-radius: 6px; cursor: pointer;
}
.conv-item.active { background: var(--el-color-primary-light-9); }
.conv-title { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chat-header { display: flex; justify-content: space-between; align-items: center; }
.chat-messages { height: 460px; overflow-y: auto; padding: 12px; background: var(--jiwei-bg-page, #f7f6f2); border-radius: 8px; margin-bottom: 10px; }
.msg { margin-bottom: 10px; display: flex; }
.msg-user { justify-content: flex-end; }
.msg-bubble {
  max-width: 78%; padding: 10px 14px; border-radius: 10px;
  font-size: 14px; line-height: 1.6; white-space: pre-wrap;
}
.msg-user .msg-bubble { background: var(--el-color-primary); color: #fff; border-bottom-right-radius: 3px; }
.msg-ai .msg-bubble { background: #fff; border: 1px solid var(--el-border-color); border-bottom-left-radius: 3px; }
.tool-list { margin-top: 8px; display: flex; gap: 6px; flex-wrap: wrap; }
.quick-questions { margin-bottom: 8px; }
.quick-tag { cursor: pointer; margin: 0 6px 6px 0; }
.chat-input { margin-top: 4px; }
</style>
