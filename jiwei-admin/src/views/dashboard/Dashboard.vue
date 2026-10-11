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
          <div class="stat-value">{{ card.value }}</div>
        </div>
      </el-card>
    </div>

    <!-- 图表区域 -->
    <el-card class="chart-card" shadow="never">
      <template #header>
        <span>数据概览 · {{ stats.date || '近 7 日' }}</span>
      </template>
      <div ref="chartRef" class="chart"></div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive, computed } from 'vue'
import { getOrderStatistics } from '@/api'
import * as echarts from 'echarts'

const chartRef = ref<HTMLDivElement>()
const stats = reactive({
  todayOrders: 0,
  todayRevenue: '0.00',
  pendingOrders: 0,
  completedOrders: 0,
  date: ''
})

const statCards = computed(() => [
  { label: '今日订单', value: stats.todayOrders, icon: 'ShoppingCart' },
  { label: '今日收入', value: `¥${stats.todayRevenue}`, icon: 'Money' },
  { label: '待处理订单', value: stats.pendingOrders, icon: 'Clock' },
  { label: '已完成订单', value: stats.completedOrders, icon: 'CircleCheck' }
])

onMounted(async () => {
  try {
    const res = await getOrderStatistics()
    Object.assign(stats, res.data)
  } catch (e) {
    console.error('获取统计数据失败', e)
  }

  if (chartRef.value) {
    renderChart(chartRef.value)
  }
})

function renderChart(el: HTMLDivElement) {
  const chart = echarts.init(el)
  chart.setOption({
    color: ['#FF7A3D', '#F5A60B'],
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#fff',
      borderColor: '#F0E4D6',
      textStyle: { color: '#33261E' }
    },
    legend: { textStyle: { color: '#6B5A4E' }, top: 0 },
    grid: { left: 10, right: 20, top: 40, bottom: 10, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
      axisLine: { lineStyle: { color: '#F0E4D6' } },
      axisTick: { show: false },
      axisLabel: { color: '#9C8A7C' }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#9C8A7C' },
      splitLine: { lineStyle: { color: '#F5ECE0' } }
    },
    series: [
      {
        name: '订单数',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: [82, 93, 90, 94, 100, 130, 120],
        lineStyle: { width: 3, color: '#FF7A3D' },
        itemStyle: { color: '#FF7A3D' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(255, 122, 61, 0.18)' },
            { offset: 1, color: 'rgba(255, 122, 61, 0)' }
          ])
        }
      },
      {
        name: '收入(百元)',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: [34, 41, 38, 42, 47, 58, 52],
        lineStyle: { width: 3, color: '#F5A60B' },
        itemStyle: { color: '#F5A60B' }
      }
    ]
  })
}
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

.chart {
  height: 360px;
}

@media (max-width: 1100px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
