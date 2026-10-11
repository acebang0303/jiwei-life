<template>
  <div class="dashboard">
    <!-- 统计卡片 -->
    <div class="stat-grid">
      <el-card v-for="card in statCards" :key="card.label" class="stat-card" shadow="never">
        <div class="stat-icon">
          <el-icon :size="22"><component :is="card.icon" /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value" :style="card.color ? { color: card.color } : null">{{ card.value }}</div>
        </div>
      </el-card>
    </div>

    <!-- 图表 + 排行 -->
    <div class="chart-grid">
      <el-card class="chart-card" shadow="never">
        <template #header><span>近 7 天订单趋势</span></template>
        <div ref="trendRef" class="chart"></div>
      </el-card>

      <el-card class="chart-card" shadow="never">
        <template #header><span>热门菜品 Top5</span></template>
        <el-table :data="topDishes" size="small" v-loading="loading">
          <el-table-column prop="name" label="菜品" />
          <el-table-column prop="sales" label="销量" width="90" align="right" />
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import http from '@/api/http'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])

const loading = ref(false)
const trendRef = ref<HTMLDivElement>()
const stats = reactive({ todayOrders: 0, todayRevenue: '0', pendingOrders: 0, completedOrders: 0 })
const topDishes = ref<{ name: string; sales: number }[]>([])
const merchantId = ref<number>(0)

async function loadMerchantId(): Promise<number | undefined> {
  if (merchantId.value) return merchantId.value
  try {
    const res = await http.get('/merchant/info')
    merchantId.value = res?.data?.id
  } catch { /* handled */ }
  return merchantId.value
}

const C = {
  primary: '#F06B18',
  glow: '#FF9A5A',
  amber: '#E8A13C',
  green: '#4C9A6A',
  label: '#9B8F82',
  axis: '#EFE7DD',
  split: '#F4EDE4'
}

const statCards = computed(() => [
  { label: '今日订单', value: stats.todayOrders, icon: 'Document' },
  { label: '今日营收', value: `¥${stats.todayRevenue}`, icon: 'Money' },
  { label: '待处理', value: stats.pendingOrders, icon: 'Clock', color: C.amber },
  { label: '已完成', value: stats.completedOrders, icon: 'CircleCheck', color: C.green }
])

async function fetchStats() {
  loading.value = true
  try {
    const mid = await loadMerchantId()
    const res = await http.get('/order/statistics', { params: { merchantId: mid } })
    Object.assign(stats, res.data)
    const dishRes = await http.get('/dish/page', { params: { page: 1, pageSize: 5, merchantId: mid } })
    topDishes.value = (dishRes.data.records || []).map((d: any) => ({ name: d.name, sales: d.sales || 0 }))
  } catch { /* use defaults */ }
  finally { loading.value = false }
}

function renderChart(el: HTMLDivElement) {
  const chart = echarts.init(el)
  chart.setOption({
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#fff',
      borderColor: '#EFE7DD',
      textStyle: { color: '#2B241E' }
    },
    grid: { left: 10, right: 20, top: 20, bottom: 10, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
      axisLine: { lineStyle: { color: C.axis } },
      axisTick: { show: false },
      axisLabel: { color: C.label }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: C.label },
      splitLine: { lineStyle: { color: C.split } }
    },
    series: [
      {
        name: '订单数',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        data: [22, 28, 25, 30, 35, 42, 38],
        itemStyle: { color: C.primary },
        lineStyle: { width: 3, color: C.primary },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(240, 107, 24, 0.2)' },
            { offset: 1, color: 'rgba(240, 107, 24, 0)' }
          ])
        }
      }
    ]
  })
}

onMounted(() => {
  fetchStats()
  if (trendRef.value) renderChart(trendRef.value)
})
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.stat-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
}

.stat-icon {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  border-radius: 14px;
  background: var(--jiwei-primary-light);
  color: var(--jiwei-primary);
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-label {
  font-size: 13px;
  color: var(--jiwei-text-secondary);
  margin-bottom: 6px;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--jiwei-text-primary);
  font-variant-numeric: tabular-nums;
}

.chart-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
}

.chart {
  height: 300px;
}

@media (max-width: 1100px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>
