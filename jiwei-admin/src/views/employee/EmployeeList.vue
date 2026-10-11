<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>员工管理</span>
        </div>
      </template>

      <div class="search-bar">
        <el-input
          v-model="searchName"
          placeholder="请输入员工姓名"
          clearable
          style="width: 240px"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" @click="handleSearch" style="margin-left: 12px">搜索</el-button>
      </div>

      <el-table :data="employees" stripe v-loading="loading" style="margin-top: 16px">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="name" label="姓名" width="120" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column label="角色" width="140">
          <template #default="{ row }">{{ roleLabel(row.roleId) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              :type="row.status === 1 ? 'danger' : 'success'"
              @click="handleToggle(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
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
import { getEmployeePage, updateEmployeeStatus } from '@/api'
import { ElMessage } from 'element-plus'
import type { Employee } from '@/types'

const loading = ref(false)
const employees = ref<Employee[]>([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const searchName = ref('')

function roleLabel(roleId: number): string {
  const map: Record<number, string> = { 1: '系统管理员', 2: '商家', 3: '运营' }
  return map[roleId] ?? '未知'
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getEmployeePage({ page: page.value, pageSize: pageSize.value, name: searchName.value || undefined })
    employees.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchData()
}

async function handleToggle(row: Employee) {
  const newStatus = row.status === 1 ? 0 : 1
  await updateEmployeeStatus(row.id, newStatus)
  row.status = newStatus
  ElMessage.success(newStatus === 1 ? '已启用' : '已禁用')
}

onMounted(fetchData)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.search-bar {
  display: flex;
  align-items: center;
}
</style>