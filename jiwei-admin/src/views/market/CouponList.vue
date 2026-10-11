<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>优惠券管理</span>
          <el-button type="primary" @click="handleAdd">新增优惠券</el-button>
        </div>
      </template>

      <el-table :data="list" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="优惠券名称" width="160" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ typeLabel(row.type) }}</template>
        </el-table-column>
        <el-table-column prop="threshold" label="门槛" width="80">
          <template #default="{ row }">¥{{ row.threshold }}</template>
        </el-table-column>
        <el-table-column label="优惠值" width="140">
          <template #default="{ row }">{{ discountDisplay(row) }}</template>
        </el-table-column>
        <el-table-column prop="totalCount" label="发放总量" width="90" />
        <el-table-column prop="perUserLimit" label="每人限领" width="90" />
        <el-table-column label="有效期(天)" width="100">
          <template #default="{ row }">{{ row.validDays }}天</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              :type="row.status === 1 ? 'warning' : 'success'"
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

    <el-dialog v-model="dialogVisible" title="新增优惠券" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="优惠券名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入优惠券名称" maxlength="20" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" placeholder="请选择类型" style="width: 100%;">
            <el-option label="满减券" :value="1" />
            <el-option label="折扣券" :value="2" />
            <el-option label="现金券" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="门槛金额" prop="threshold">
          <el-input-number v-model="form.threshold" :min="0" :precision="2" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="优惠值" prop="discountValue">
          <el-input-number v-model="form.discountValue" :min="0" :precision="2" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="发放总量" prop="totalCount">
          <el-input-number v-model="form.totalCount" :min="1" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="每人限领" prop="perUserLimit">
          <el-input-number v-model="form.perUserLimit" :min="1" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="有效期(天)" prop="validDays">
          <el-input-number v-model="form.validDays" :min="1" style="width: 100%;" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getCouponTemplatePage, createCouponTemplate, updateCouponStatus } from '@/api'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { CouponTemplate } from '@/types'

const loading = ref(false)
const submitting = ref(false)
const list = ref<CouponTemplate[]>([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  type: undefined as number | undefined,
  threshold: 0,
  discountValue: 0,
  totalCount: 100,
  perUserLimit: 1,
  validDays: 30
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入优惠券名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  threshold: [{ required: true, message: '请输入门槛金额', trigger: 'blur' }],
  discountValue: [{ required: true, message: '请输入优惠值', trigger: 'blur' }],
  totalCount: [{ required: true, message: '请输入发放总量', trigger: 'blur' }],
  perUserLimit: [{ required: true, message: '请输入每人限领数量', trigger: 'blur' }],
  validDays: [{ required: true, message: '请输入有效期', trigger: 'blur' }]
}

const typeMap: Record<number, string> = { 1: '满减券', 2: '折扣券', 3: '现金券' }

function typeLabel(type: number) {
  return typeMap[type] || '未知'
}

function discountDisplay(row: CouponTemplate) {
  if (row.type === 1) return `满${row.threshold}减${row.discountValue}`
  if (row.type === 2) return `${(row.discountValue * 10).toFixed(1)}折`
  if (row.type === 3) return `${row.discountValue}元`
  return '-'
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getCouponTemplatePage({ page: page.value, pageSize: pageSize.value })
    list.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  dialogVisible.value = true
}

async function handleToggle(row: CouponTemplate) {
  const newStatus = row.status === 1 ? 0 : 1
  await updateCouponStatus(row.id, newStatus)
  row.status = newStatus
  ElMessage.success(newStatus === 1 ? '已启用' : '已禁用')
}

function resetForm() {
  formRef.value?.resetFields()
  form.type = undefined
  form.threshold = 0
  form.discountValue = 0
  form.totalCount = 100
  form.perUserLimit = 1
  form.validDays = 30
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await createCouponTemplate({ ...form } as CouponTemplate)
    ElMessage.success('优惠券模板创建成功')
    dialogVisible.value = false
    resetForm()
    page.value = 1
    fetchData()
  } finally {
    submitting.value = false
  }
}

onMounted(fetchData)
</script>

<style scoped>
.page-container { padding: 0; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
