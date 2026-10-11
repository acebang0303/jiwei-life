<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>菜品管理</span>
          <el-button type="primary" size="small" @click="handleAdd">新增菜品</el-button>
        </div>
      </template>
      <el-table :data="dishes" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="图片" width="70">
          <template #default="{ row }"><el-avatar v-if="row.image" :src="row.image" shape="square" /></template>
        </el-table-column>
        <el-table-column prop="name" label="菜品名称" width="140" />
        <el-table-column prop="price" label="价格" width="90">
          <template #default="{ row }">¥{{ row.price }}</template>
        </el-table-column>
        <el-table-column prop="sales" label="销量" width="80" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '上架' : '下架' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'" @click="handleToggle(row)">
              {{ row.status === 1 ? '下架' : '上架' }}
            </el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
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
import { ref, onMounted } from 'vue'
import http from '@/api/http'

const loading = ref(false)
const dishes = ref<any[]>([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const merchantId = ref<number>(0)

// 解析当前登录商家的店铺 ID（一次获取后缓存）
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
    const mid = await loadMerchantId()
    const res = await http.get('/dish/page', { params: { page: page.value, pageSize: pageSize.value, merchantId: mid } })
    dishes.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function handleAdd() { ElMessage.info('请使用管理端新增菜品') }
function handleEdit(row: any) { ElMessage.info(`编辑: ${row.name}`) }

async function handleToggle(row: any) {
  const newStatus = row.status === 1 ? 0 : 1
  await http.put(`/dish/${row.id}/status`, null, { params: { status: newStatus } })
  row.status = newStatus
  ElMessage.success(newStatus === 1 ? '已上架' : '已下架')
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确认删除该菜品？', '提示', { type: 'warning' })
  await http.delete('/dish', { params: { ids: [id] } })
  ElMessage.success('删除成功')
  fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.page-container { padding: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
