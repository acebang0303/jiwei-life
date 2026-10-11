<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>订单处理</span>
          <el-select v-model="statusFilter" placeholder="筛选状态" clearable style="width: 130px" @change="fetchData">
            <el-option v-for="(label, key) in statusMap" :key="key" :label="label" :value="Number(key)" />
          </el-select>
        </div>
      </template>

      <el-table :data="orders" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="number" label="订单号" width="170" />
        <el-table-column label="金额" width="90">
          <template #default="{ row }">¥{{ row.payAmount || row.amount }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ statusMap[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="addressDetail" label="地址" min-width="180" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" width="100" show-overflow-tooltip />
        <el-table-column prop="createTime" label="下单时间" width="160" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 2" size="small" type="success" @click="handleAction(row.id, 'confirm')">接单</el-button>
            <el-button v-if="row.status === 2" size="small" type="danger" @click="handleAction(row.id, 'reject')">拒单</el-button>
            <el-button v-if="row.status === 3" size="small" type="primary" @click="handleAction(row.id, 'prepare')">备货完成</el-button>
            <el-button v-if="row.status === 4" size="small" type="success" @click="handleAction(row.id, 'complete')">完成</el-button>
            <el-tag v-if="row.status === 5 || row.status === 6 || row.status === 7" type="info">已结束</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page" v-model:page-size="pageSize" :total="total"
        layout="total, prev, pager, next" @current-change="fetchData"
        style="margin-top: 20px; justify-content: center"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import http from '@/api/http'
import { ElNotification } from 'element-plus'

const loading = ref(false)
const orders = ref<any[]>([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const statusFilter = ref<number | undefined>(undefined)
const merchantId = ref<number>(0)

const statusMap: Record<number, string> = { 1: '待支付', 2: '待接单', 3: '备货中', 4: '待取餐', 5: '已完成', 6: '已取消', 7: '已退款' }
const statusTag = (s: number) => ({ 1: 'warning', 2: 'info', 3: '', 4: 'primary', 5: 'success', 6: 'danger', 7: 'info' } as any)[s] || 'info'

async function loadMerchantId(): Promise<number | undefined> {
  if (merchantId.value) return merchantId.value
  try {
    const res = await http.get('/merchant/info')
    merchantId.value = res?.data?.id
  } catch { /* handled */ }
  return merchantId.value
}

async function fetchData() {
  loading.value = true
  try {
    const params: any = { page: page.value, pageSize: pageSize.value }
    if (statusFilter.value) params.status = statusFilter.value
    params.merchantId = await loadMerchantId()
    const res = await http.get('/order/page', { params })
    orders.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

async function handleAction(id: number, action: string) {
  const labels: Record<string, string> = { confirm: '接单', reject: '拒单', prepare: '备货完成', complete: '完成' }
  if (action === 'reject') {
    const { value } = await ElMessageBox.prompt('拒单原因', '拒单', { type: 'warning' }).catch(() => ({ value: '' }))
    if (!value) return
    await http.put(`/order/${id}/reject`, null, { params: { reason: value } })
  } else {
    await ElMessageBox.confirm(`确认${labels[action]}？`, '提示', { type: 'warning' })
    await http.put(`/order/${id}/${action}`)
  }
  ElMessage.success(`${labels[action]}成功`)
  fetchData()
}

let ws: WebSocket | null = null

function connectWs() {
  const empId = localStorage.getItem('empId')
  if (!empId) return
  // 连接后端通知 WebSocket（/ws/{empId}）
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  ws = new WebSocket(`${proto}://${location.host}/ws/${empId}`)
  ws.onmessage = (e) => {
    try {
      const msg = JSON.parse(e.data)
      if (msg.type === 'newOrder') {
        const data = JSON.parse(msg.content || '{}')
        ElNotification({
          title: '🔔 新订单提醒',
          message: `订单 ${data.orderNo || ''} 已支付，金额 ¥${data.amount ?? ''}，请及时接单`,
          type: 'success',
          duration: 0
        })
        fetchData()
      } else if (msg.type === 'remind') {
        const data = JSON.parse(msg.content || '{}')
        ElNotification({
          title: '⏰ 催单提醒',
          message: `订单 ${data.orderNo || ''} 被用户催单，请尽快处理`,
          type: 'warning',
          duration: 0
        })
        fetchData()
      }
    } catch { /* 忽略无法解析的消息 */ }
  }
  ws.onclose = () => {
    // 断线 5 秒后重连
    setTimeout(connectWs, 5000)
  }
}

onMounted(() => {
  fetchData()
  connectWs()
})

onBeforeUnmount(() => {
  if (ws) ws.close()
})
</script>

<style scoped>
.page-container { padding: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
