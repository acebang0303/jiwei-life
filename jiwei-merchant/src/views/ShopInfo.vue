<template>
  <div class="page-container">
    <el-card v-loading="loading">
      <template #header><span>店铺信息</span></template>
      <el-form v-if="shop" :model="shop" label-width="100px" style="max-width: 600px">
        <el-form-item label="店铺名称">
          <el-input v-model="shop.name" />
        </el-form-item>
        <el-form-item label="店铺Logo">
          <el-input v-model="shop.logo" placeholder="图片URL" />
          <el-avatar v-if="shop.logo" :src="shop.logo" :size="60" shape="square" style="margin-top:8px" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="shop.phone" />
        </el-form-item>
        <el-form-item label="店铺地址">
          <el-input v-model="shop.address" />
        </el-form-item>
        <el-form-item label="营业时间">
          <el-input v-model="shop.businessHours" placeholder="如 10:00-22:00" />
        </el-form-item>
        <el-form-item label="配送范围(米)">
          <el-input-number v-model="shop.deliveryRange" :min="500" :step="500" />
        </el-form-item>
        <el-form-item label="店铺简介">
          <el-input v-model="shop.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="营业状态">
          <el-tag :type="shop.status === 1 ? 'success' : shop.status === 2 ? 'warning' : 'info'">
            {{ { 0: '待审核', 1: '营业中', 2: '休息中', 3: '已关闭' }[String(shop.status)] || '未知' }}
          </el-tag>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSave" :loading="saving">保存修改</el-button>
        </el-form-item>
      </el-form>
      <el-empty v-else description="未找到店铺信息" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import http from '@/api/http'

const loading = ref(false)
const saving = ref(false)
const shop = ref<any>(null)

async function fetchShop() {
  loading.value = true
  try {
    const res = await http.get('/merchant/info')
    shop.value = res.data
  } catch { /* handled */ }
  finally { loading.value = false }
}

async function handleSave() {
  saving.value = true
  try {
    await http.put('/merchant/info', shop.value)
    ElMessage.success('保存成功')
  } catch { /* handled */ }
  finally { saving.value = false }
}

onMounted(fetchShop)
</script>

<style scoped>
.page-container { padding: 16px; }
</style>
