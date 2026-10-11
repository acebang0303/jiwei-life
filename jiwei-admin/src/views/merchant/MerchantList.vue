<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>商户管理</span>
          <el-button type="primary" @click="fetchData">刷新</el-button>
        </div>
      </template>

      <el-table :data="merchants" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="店铺名称" min-width="160" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : row.status === 0 ? 'warning' : 'info'">
              {{ row.status === 1 ? '营业中' : row.status === 0 ? '待审核' : '休息中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="联系电话" width="130" />
        <el-table-column prop="address" label="地址" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" type="success" size="small" @click="handleAudit(row.id, 1)">
              通过
            </el-button>
            <el-button v-if="row.status === 0" type="danger" size="small" @click="handleAudit(row.id, 3)">
              驳回
            </el-button>
            <el-button v-if="row.status !== 0" size="small" @click="handleToggle(row.id, row.status === 1 ? 2 : 1)">
              {{ row.status === 1 ? '设为休息' : '设为营业' }}
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
import { getMerchantPage, auditMerchant } from '@/api'
import { ElMessage } from 'element-plus'
import type { MerchantInfo } from '@/types'

const loading = ref(false)
const merchants = ref<MerchantInfo[]>([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

async function fetchData() {
  loading.value = true
  try {
    const res = await getMerchantPage({ page: page.value, pageSize: pageSize.value })
    merchants.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function handleAudit(id: number, status: number) {
  await auditMerchant(id, status)
  ElMessage.success(status === 1 ? '审核通过' : '已驳回')
  fetchData()
}

async function handleToggle(id: number, status: number) {
  await auditMerchant(id, status)
  ElMessage.success('状态更新成功')
  fetchData()
}

onMounted(fetchData)
</script>
