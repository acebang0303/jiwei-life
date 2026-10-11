<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>菜品管理</span>
          <el-button type="primary" @click="handleAdd">新增菜品</el-button>
        </div>
      </template>

      <div style="margin-bottom: 12px">
        <el-select v-model="merchantId" placeholder="全部商户" clearable style="width: 220px" @change="onMerchantChange">
          <el-option v-for="m in merchants" :key="m.id" :label="m.name" :value="m.id" />
        </el-select>
      </div>

      <el-table :data="dishes" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="图片" width="80">
          <template #default="{ row }">
            <el-avatar v-if="row.image" :src="row.image" shape="square" />
          </template>
        </el-table-column>
        <el-table-column prop="name" label="菜品名称" width="140" />
        <el-table-column prop="price" label="价格" width="100">
          <template #default="{ row }">¥{{ row.price }}</template>
        </el-table-column>
        <el-table-column prop="sales" label="销量" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
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
import { ref, onMounted } from 'vue'
import { getDishPage, getMerchantPage, updateDishStatus, deleteDish } from '@/api'
import { ElMessage } from 'element-plus'
import type { Dish } from '@/types'

const loading = ref(false)
const dishes = ref<Dish[]>([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const merchants = ref<any[]>([])
const merchantId = ref<number>()

async function loadMerchants() {
  try {
    const res = await getMerchantPage({ page: 1, pageSize: 100 })
    merchants.value = res.data?.records || []
  } catch { /* handled */ }
}

function onMerchantChange() {
  page.value = 1
  fetchData()
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getDishPage({ page: page.value, pageSize: pageSize.value, merchantId: merchantId.value })
    dishes.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function handleAdd() { ElMessage.info('请在完整版本中使用弹窗表单') }
function handleEdit(row: Dish) { ElMessage.info(`编辑菜品: ${row.name}`) }

async function handleToggle(row: Dish) {
  const newStatus = row.status === 1 ? 0 : 1
  await updateDishStatus(row.id, newStatus)
  row.status = newStatus
  ElMessage.success(newStatus === 1 ? '已上架' : '已下架')
}

async function handleDelete(id: number) {
  await deleteDish([id])
  ElMessage.success('删除成功')
  fetchData()
}

onMounted(() => {
  loadMerchants()
  fetchData()
})
</script>
