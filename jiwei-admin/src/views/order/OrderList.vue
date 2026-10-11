<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>订单管理</span>
          <div>
            <el-select v-model="filter.status" placeholder="订单状态" clearable style="width: 140px; margin-right: 12px;">
              <el-option label="待接单" :value="2" />
              <el-option label="备货中" :value="3" />
              <el-option label="待取餐" :value="4" />
              <el-option label="已完成" :value="5" />
              <el-option label="已取消" :value="6" />
            </el-select>
            <el-button type="primary" @click="fetchData">查询</el-button>
          </div>
        </div>
      </template>

      <el-table :data="orders" stripe v-loading="loading">
        <el-table-column prop="id" label="订单ID" width="80" />
        <el-table-column prop="number" label="订单号" width="180" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="payAmount" label="金额" width="100">
          <template #default="{ row }">¥{{ row.payAmount }}</template>
        </el-table-column>
        <el-table-column prop="addressDetail" label="地址" min-width="200" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 2" type="success" size="small" @click="handleConfirm(row.id)">接单</el-button>
            <el-button v-if="row.status === 2" type="danger" size="small" @click="handleReject(row.id)">拒单</el-button>
            <el-button v-if="row.status === 4" type="primary" size="small" @click="handleComplete(row.id)">完成</el-button>
            <el-button v-if="row.payStatus === 1 && row.status !== 7" type="warning" size="small" @click="handleRefund(row.id)">退款</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="fetchData"
        style="margin-top: 20px; justify-content: center;"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { getOrderPage, confirmOrder, rejectOrder, completeOrder, refundOrder } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Order } from '@/types'

const loading = ref(false)
const orders = ref<Order[]>([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const filter = reactive({ status: undefined as number | undefined })

const statusMap: Record<number, string> = {
  1: '待支付', 2: '待接单', 3: '备货中', 4: '待取餐', 5: '已完成', 6: '已取消', 7: '已退款'
}

function statusLabel(status: number) {
  return statusMap[status] || '未知'
}

function statusTag(status: number): 'info' | 'success' | 'warning' | 'danger' {
  if (status === 5) return 'success'
  if (status === 6 || status === 7) return 'info'
  if (status === 1) return 'danger'
  return 'warning'
}

async function fetchData() {
  loading.value = true
  try {
    // undefined/null 不传 status，避免 axios 序列化成 status=undefined 导致后端转换报错
    const params: any = { page: page.value, pageSize: pageSize.value }
    if (filter.status !== undefined && filter.status !== null) params.status = filter.status
    const res = await getOrderPage(params)
    orders.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function handleConfirm(id: number) {
  await confirmOrder(id)
  ElMessage.success('接单成功')
  fetchData()
}

async function handleReject(id: number) {
  try {
    await ElMessageBox.prompt('请输入拒单原因', '拒单', { confirmButtonText: '确定' })
  } catch {
    return
  }
  await rejectOrder(id, '商家主动拒单')
  ElMessage.success('拒单成功')
  fetchData()
}

async function handleComplete(id: number) {
  await completeOrder(id)
  ElMessage.success('订单完成')
  fetchData()
}

async function handleRefund(id: number) {
  await refundOrder(id)
  ElMessage.success('退款处理完成')
  fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.page-container { padding: 0; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
