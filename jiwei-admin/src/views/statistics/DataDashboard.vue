<template>
  <div class="dashboard">
    <!-- Row 1 -->
    <div class="grid">
      <div class="cell">
        <div class="card-title">今日概览</div>
        <div class="stat-row">
          <el-statistic title="今日订单数" :value="stats.todayOrders" />
          <el-statistic title="今日营收" :value="'¥' + stats.todayRevenue" />
          <el-statistic title="待处理订单" :value="stats.pendingOrders" :value-style="accent.warning" />
          <el-statistic title="已完成订单" :value="stats.completedOrders" :value-style="accent.success" />
        </div>
      </div>

      <div class="cell">
        <div class="card-title">订单趋势</div>
        <v-chart :option="lineOption" style="height: 300px" />
      </div>

      <div class="cell">
        <div class="card-title">订单状态</div>
        <v-chart :option="pieOption" style="height: 300px" />
      </div>
    </div>

    <!-- Row 2 -->
    <div class="grid">
      <div class="cell">
        <div class="card-title">商户排行</div>
        <v-chart :option="barOption" style="height: 300px" />
      </div>

      <div class="cell">
        <div class="card-title">菜品排行</div>
        <v-chart :option="hBarOption" style="height: 300px" />
      </div>

      <div class="cell">
        <div class="card-title">社区数据</div>
        <div class="stat-row">
          <el-statistic title="笔记总数" :value="4" />
          <el-statistic title="评价总数" :value="5" />
          <el-statistic title="用户总数" :value="5" />
          <el-statistic title="今日新增" :value="2" :value-style="accent.success" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import VChart from 'vue-echarts'
import * as echarts from 'echarts'
import { getOrderStatistics } from '@/api'

const C = {
  primary: '#FF7A3D',
  amber: '#E8A13C',
  green: '#4C9A6A',
  blue: '#5B8DB8',
  red: '#E05B4A',
  grey: '#9C8A7C',
  text: '#33261E',
  label: '#9C8A7C',
  axis: '#F0E4D6',
  split: '#F5ECE0'
}

const accent = {
  warning: { color: C.amber, fontSize: '24px', fontWeight: 700 },
  success: { color: C.green, fontSize: '24px', fontWeight: 700 }
}

const stats = reactive({
  todayOrders: 0,
  todayRevenue: '0.00',
  pendingOrders: 0,
  completedOrders: 0
})

const tooltipStyle = {
  backgroundColor: '#fff',
  borderColor: '#F0E4D6',
  textStyle: { color: '#33261E' }
}

// ============ 订单趋势 - line ============
const days = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const orderData = Array.from({ length: 7 }, () => Math.floor(Math.random() * 61) + 20)

const lineOption = ref({
  tooltip: { trigger: 'axis', ...tooltipStyle },
  legend: { textStyle: { color: C.text }, top: 0 },
  grid: { left: 10, right: 20, top: 40, bottom: 10, containLabel: true },
  xAxis: {
    type: 'category',
    data: days,
    boundaryGap: false,
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
      data: orderData,
      itemStyle: { color: C.primary },
      lineStyle: { width: 3, color: C.primary },
      areaStyle: { color: 'rgba(255, 122, 61, 0.12)' }
    }
  ]
})

// ============ 订单状态 - pie ============
const pieOption = ref({
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)', ...tooltipStyle },
  legend: {
    orient: 'vertical',
    right: 10,
    top: 'center',
    textStyle: { color: C.text }
  },
  series: [
    {
      type: 'pie',
      radius: ['50%', '75%'],
      center: ['40%', '52%'],
      avoidLabelOverlap: false,
      padAngle: 2,
      itemStyle: { borderRadius: 4 },
      label: { show: false },
      emphasis: {
        label: { show: true, fontSize: 16, fontWeight: 'bold', color: C.text }
      },
      data: [
        { value: 5, name: '待支付', itemStyle: { color: C.amber } },
        { value: 3, name: '待接单', itemStyle: { color: C.blue } },
        { value: 4, name: '备货中', itemStyle: { color: C.grey } },
        { value: 35, name: '已完成', itemStyle: { color: C.green } },
        { value: 8, name: '已取消', itemStyle: { color: C.red } }
      ]
    }
  ]
})

// ============ 商户排行 - bar ============
const barOption = ref({
  tooltip: { trigger: 'axis', ...tooltipStyle },
  grid: { left: 10, right: 20, top: 20, bottom: 10, containLabel: true },
  xAxis: {
    type: 'category',
    data: ['张记面馆', '老王烧烤', '蜀味川菜', '外婆家'],
    axisLabel: { color: C.label, rotate: 15 },
    axisLine: { lineStyle: { color: C.axis } },
    axisTick: { show: false }
  },
  yAxis: {
    type: 'value',
    axisLabel: { color: C.label },
    splitLine: { lineStyle: { color: C.split } }
  },
  series: [
    {
      type: 'bar',
      barWidth: 36,
      data: [320, 280, 210, 180],
      itemStyle: {
        color: C.primary,
        borderRadius: [6, 6, 0, 0]
      },
      label: { show: true, position: 'top', color: C.label }
    }
  ]
})

// ============ 菜品排行 - horizontal bar ============
const dishNames = ['青岛啤酒', '羊肉串', '蛋炒饭', '麻婆豆腐', '红烧牛肉面']
const dishValues = [4500, 3200, 2300, 1560, 1523]

const hBarOption = ref({
  tooltip: { trigger: 'axis', ...tooltipStyle },
  grid: { left: 10, right: 40, top: 10, bottom: 10, containLabel: true },
  xAxis: {
    type: 'value',
    axisLabel: { color: C.label },
    splitLine: { lineStyle: { color: C.split } }
  },
  yAxis: {
    type: 'category',
    data: dishNames,
    axisLabel: { color: C.label },
    axisLine: { lineStyle: { color: C.axis } },
    inverse: true
  },
  series: [
    {
      type: 'bar',
      barWidth: 18,
      data: dishValues.map((v) => ({
        value: v,
        itemStyle: { color: C.primary, borderRadius: [0, 6, 6, 0] }
      })),
      label: { show: true, position: 'right', color: C.label }
    }
  ]
})

onMounted(async () => {
  try {
    const res = await getOrderStatistics()
    if (res && res.data) {
      Object.assign(stats, res.data)
    }
  } catch (e) {
    console.error('获取统计数据失败', e)
  }
})
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
}

.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.cell {
  background: var(--jiwei-bg-card);
  border: 1px solid var(--jiwei-border);
  border-radius: 14px;
  padding: 16px 20px;
  box-shadow: var(--jiwei-shadow-sm);
}

.card-title {
  color: var(--jiwei-text-primary);
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--jiwei-border);
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-title::before {
  content: '';
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 35%, var(--jiwei-glow), var(--jiwei-primary));
  box-shadow: 0 1px 3px rgba(255, 122, 61, 0.35);
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px 20px;
}

.stat-row :deep(.el-statistic__head) {
  color: var(--jiwei-text-secondary);
  font-size: 13px;
  margin-bottom: 4px;
}

.stat-row :deep(.el-statistic__content) {
  color: var(--jiwei-text-primary);
  font-size: 24px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 1100px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
