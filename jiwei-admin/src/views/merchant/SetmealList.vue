<template>
  <div class="page-container">
    <el-card>
      <template #header><span>套餐管理</span></template>

      <el-table :data="list" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="图片" width="80">
          <template #default="{ row }">
            <el-avatar v-if="row.image" :src="row.image" shape="square" />
          </template>
        </el-table-column>
        <el-table-column prop="name" label="套餐名称" width="160" />
        <el-table-column label="价格" width="100">
          <template #default="{ row }">¥{{ row.price }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
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
import { getSetmealPage, updateSetmeal, deleteSetmeal } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Setmeal } from '@/types'

const loading = ref(false)
const list = ref<Setmeal[]>([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

async function fetchData() {
  loading.value = true
  try {
    const res = await getSetmealPage({ page: page.value, pageSize: pageSize.value })
    list.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function handleToggle(row: Setmeal) {
  const newStatus = row.status === 1 ? 0 : 1
  await updateSetmeal(row.id, { status: newStatus } as Setmeal)
  row.status = newStatus
  ElMessage.success(newStatus === 1 ? '已上架' : '已下架')
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确定删除该套餐吗？', '提示', {
    type: 'warning',
  })
  await deleteSetmeal([id])
  ElMessage.success('删除成功')
  fetchData()
}

onMounted(fetchData)
</script>
