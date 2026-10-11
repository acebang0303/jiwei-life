<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>秒杀活动管理</span>
          <div>
            <el-button type="primary" @click="openAddDialog">新增秒杀</el-button>
          </div>
        </div>
      </template>

      <el-table :data="list" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="活动名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="dishId" label="秒杀菜品ID" width="120" />
        <el-table-column label="秒杀价格" width="110">
          <template #default="{ row }">¥{{ row.seckillPrice }}</template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="80" />
        <el-table-column prop="limitPerUser" label="每人限购" width="100" />
        <el-table-column prop="startTime" label="开始时间" width="170" />
        <el-table-column prop="endTime" label="结束时间" width="170" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
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

    <el-dialog v-model="dialogVisible" title="新增秒杀活动" width="520px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="活动名称" prop="name">
          <el-input v-model="form.name" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="秒杀菜品ID" prop="dishId">
          <el-input-number v-model="form.dishId" :min="1" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="秒杀价格" prop="seckillPrice">
          <el-input-number v-model="form.seckillPrice" :min="0.01" :precision="2" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="库存" prop="stock">
          <el-input-number v-model="form.stock" :min="1" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="每人限购" prop="limitPerUser">
          <el-input-number v-model="form.limitPerUser" :min="1" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-date-picker
            v-model="form.startTime"
            type="datetime"
            placeholder="选择开始时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker
            v-model="form.endTime"
            type="datetime"
            placeholder="选择结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%;"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getSeckillPage, createSeckill } from '@/api'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { SeckillActivity } from '@/types'

const loading = ref(false)
const submitting = ref(false)
const list = ref<SeckillActivity[]>([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  name: '',
  dishId: undefined as number | undefined,
  seckillPrice: undefined as number | undefined,
  stock: undefined as number | undefined,
  limitPerUser: 1,
  startTime: '',
  endTime: '',
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入活动名称', trigger: 'blur' }],
  dishId: [{ required: true, message: '请输入秒杀菜品ID', trigger: 'blur' }],
  seckillPrice: [{ required: true, message: '请输入秒杀价格', trigger: 'blur' }],
  stock: [{ required: true, message: '请输入库存', trigger: 'blur' }],
  limitPerUser: [{ required: true, message: '请输入每人限购数量', trigger: 'blur' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
}

const statusMap: Record<number, string> = {
  0: '未开始',
  1: '进行中',
  2: '已结束',
}

function statusLabel(status: number) {
  return statusMap[status] || '未知'
}

function statusTag(status: number): 'info' | 'danger' | 'success' {
  if (status === 0) return 'info'
  if (status === 1) return 'danger'
  return 'success'
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getSeckillPage({ page: page.value, pageSize: pageSize.value })
    list.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function openAddDialog() {
  dialogVisible.value = true
}

function resetForm() {
  formRef.value?.resetFields()
  form.name = ''
  form.dishId = undefined
  form.seckillPrice = undefined
  form.stock = undefined
  form.limitPerUser = 1
  form.startTime = ''
  form.endTime = ''
}

async function handleSubmit() {
  const valid = await formRef.value!.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await createSeckill({
      id: 0,
      name: form.name,
      dishId: form.dishId!,
      seckillPrice: form.seckillPrice!,
      stock: form.stock!,
      limitPerUser: form.limitPerUser!,
      startTime: form.startTime,
      endTime: form.endTime,
      status: 0,
    } as SeckillActivity)
    ElMessage.success('创建成功')
    dialogVisible.value = false
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
