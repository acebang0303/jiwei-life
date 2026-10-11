<template>
  <div class="screen">
    <header class="header">
      <h1>吉味生活 · JiweiLife 数据大屏</h1>
      <div class="time">{{ time }}</div>
    </header>
    <main class="grid">
      <!-- 左上：实时订单数 -->
      <div class="card"><h3>实时订单量</h3><div ref="orderRef" class="chart"></div></div>
      <!-- 中上：核心指标 -->
      <div class="card row-span-2">
        <h3>核心指标</h3>
        <div class="kpi-grid">
          <div class="kpi"><span class="kpi-val">{{ kpi.todayOrders }}</span><span class="kpi-label">今日订单</span></div>
          <div class="kpi"><span class="kpi-val">¥{{ kpi.todayRevenue }}</span><span class="kpi-label">今日营收</span></div>
          <div class="kpi"><span class="kpi-val">{{ kpi.pendingOrders }}</span><span class="kpi-label">待处理</span></div>
          <div class="kpi"><span class="kpi-val">{{ kpi.onlineShops }}</span><span class="kpi-label">在线商户</span></div>
        </div>
        <!-- 热力图 -->
        <div ref="heatRef" class="chart" style="height:200px;margin-top:16px"></div>
      </div>
      <!-- 右上：营收趋势 -->
      <div class="card"><h3>营收趋势</h3><div ref="revenueRef" class="chart"></div></div>
      <!-- 左下：城市订单分布 -->
      <div class="card col-span-2 row-span-2"><h3>城市订单分布</h3><div ref="mapRef" class="chart" style="height:calc(100% - 40px)"></div></div>
      <!-- 右下：实时滚动 -->
      <div class="card"><h3>最新订单</h3><div class="scroll-list">
        <div v-for="(o,i) in recentOrders" :key="i" class="order-item">{{o.time}} - {{o.shop}} · ¥{{o.amount}}</div>
      </div></div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive, onUnmounted } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, HeatmapChart, EffectScatterChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, VisualMapComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([BarChart, LineChart, HeatmapChart, EffectScatterChart, GridComponent, TooltipComponent, VisualMapComponent, CanvasRenderer])

const C = {
  primary: '#FF7A3D',
  amber: '#E8A13C',
  glow: '#FFB98A',
  cream: '#FFC38B',
  green: '#4C9A6A',
  axis: '#6B5A4E',
  sub: '#9C8A7C',
  split: 'rgba(255, 122, 61, 0.08)'
}

const time = ref('')
const orderRef = ref(); const heatRef = ref(); const revenueRef = ref(); const mapRef = ref()
const kpi = reactive({ todayOrders: 286, todayRevenue: '12,580', pendingOrders: 23, onlineShops: 48 })
const recentOrders = ref([
  { time: '19:45:12', shop: '吉味川菜馆', amount: 128 },
  { time: '19:44:38', shop: '江南小厨', amount: 58 },
  { time: '19:43:05', shop: '王品牛排', amount: 280 },
  { time: '19:42:21', shop: '绿茶餐厅', amount: 86 },
  { time: '19:41:50', shop: '海底捞', amount: 156 }
])

// 更新时间
const timer = setInterval(() => { time.value = new Date().toLocaleString('zh-CN') }, 1000)
onUnmounted(() => clearInterval(timer))

onMounted(() => {
  // 实时订单量 - 柱状图
  const oC = echarts.init(orderRef.value)
  oC.setOption({
    grid: { top: 10, right: 10, bottom: 20, left: 40 },
    xAxis: { type: 'category', data: ['12:00','13:00','14:00','15:00','16:00','17:00','18:00','19:00'], axisLabel: { color: C.sub, fontSize: 10 }, axisLine: { lineStyle: { color: C.split } } },
    yAxis: { type: 'value', axisLabel: { color: C.sub }, splitLine: { lineStyle: { color: C.split } } },
    series: [{ data: [15,22,18,20,25,35,48,52], type: 'bar', itemStyle: { color: C.primary, borderRadius: [4,4,0,0] } }]
  })

  // 营收趋势 - 折线图
  const rC = echarts.init(revenueRef.value)
  rC.setOption({
    grid: { top: 10, right: 10, bottom: 20, left: 50 },
    xAxis: { type: 'category', data: ['12日','13日','14日','15日','16日','17日','18日'], axisLabel: { color: C.sub, fontSize: 10 }, axisLine: { lineStyle: { color: C.split } } },
    yAxis: { type: 'value', axisLabel: { color: C.sub }, splitLine: { lineStyle: { color: C.split } } },
    series: [{ data: [9800,11000,10500,12000,13500,14000,12580], type: 'line', smooth: true, symbol: 'circle', symbolSize: 6, lineStyle: { color: C.amber, width: 2 }, itemStyle: { color: C.amber }, areaStyle: { color: new echarts.graphic.LinearGradient(0,0,0,1,[{offset:0,color:'rgba(232,161,60,0.28)'},{offset:1,color:'rgba(232,161,60,0)'}]) } }]
  })

  // 热力图（模拟24小时×7天）
  const hC = echarts.init(heatRef.value)
  const hours = ['00:00','02:00','04:00','06:00','08:00','10:00','12:00','14:00','16:00','18:00','20:00','22:00']
  const days = ['周一','周二','周三','周四','周五','周六','周日']
  const heatData: [number,number,number][] = []
  for (let h = 0; h < 12; h++) for (let d = 0; d < 7; d++) heatData.push([h, d, Math.floor(Math.random() * 100)])
  hC.setOption({
    grid: { top: 10, right: 20, bottom: 20, left: 50 },
    xAxis: { type: 'category', data: hours, axisLabel: { color: C.sub, fontSize: 9 }, axisLine: { lineStyle: { color: C.split } } },
    yAxis: { type: 'category', data: days, axisLabel: { color: C.sub, fontSize: 9 } },
    visualMap: { min: 0, max: 100, calculable: true, orient: 'horizontal', left: 'center', bottom: 0, inRange: { color: ['#171210','#5B3A20','#FF7A3D','#FFC38B'] }, show: false },
    series: [{ type: 'heatmap', data: heatData, label: { show: false } }]
  })

  // 城市订单分布 - 经纬度散点（暖橙发光）
  const mC = echarts.init(mapRef.value)
  const coord: Record<string, [number, number]> = {
    '杭州': [120.15, 30.28], '宁波': [121.55, 29.87], '温州': [120.70, 28.00],
    '嘉兴': [120.76, 30.75], '湖州': [120.09, 30.89], '绍兴': [120.58, 30.01], '金华': [119.65, 29.08]
  }
  const geoData: [number, number, number][] = []
  const cityNames = Object.keys(coord)
  for (let i = 0; i < 60; i++) {
    const name = cityNames[Math.floor(Math.random() * cityNames.length)]
    const [lng, lat] = coord[name]
    geoData.push([lng + (Math.random() - 0.5) * 0.3, lat + (Math.random() - 0.5) * 0.3, Math.floor(Math.random() * 20)])
  }
  mC.setOption({
    grid: { top: 10, right: 20, bottom: 30, left: 50 },
    xAxis: { type: 'value', min: 119, max: 122.5, axisLabel: { color: C.sub, fontSize: 9 }, splitLine: { lineStyle: { color: C.split } } },
    yAxis: { type: 'value', min: 27.5, max: 31.2, axisLabel: { color: C.sub, fontSize: 9 }, splitLine: { lineStyle: { color: C.split } } },
    series: [{
      type: 'effectScatter', data: geoData,
      symbolSize: (v: number[]) => (v[2] || 1) * 1.5 + 4,
      rippleEffect: { brushType: 'stroke' },
      itemStyle: { color: C.primary, shadowBlur: 10, shadowColor: 'rgba(255, 122, 61, 0.6)' }
    }]
  })
})
</script>

<style>
.screen {
  width: 100vw;
  height: 100vh;
  background:
    radial-gradient(circle at 15% -10%, rgba(255, 122, 61, 0.16), transparent 45%),
    radial-gradient(circle at 85% 110%, rgba(232, 161, 60, 0.10), transparent 45%),
    #171210;
  padding: 8px 16px;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0 12px;
  border-bottom: 1px solid rgba(255, 122, 61, 0.25);
  margin-bottom: 12px;
}
.header h1 {
  font-size: 24px;
  background: linear-gradient(90deg, #FFB98A, #FF7A3D, #E8A13C);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}
.header .time { font-size: 16px; color: #FF9A5A; font-family: monospace; }
.grid { display: grid; grid-template-columns: 1fr 1fr 1fr; grid-template-rows: 1fr 1fr; gap: 12px; height: calc(100vh - 70px); }
.card {
  background: rgba(40, 32, 26, 0.72);
  border: 1px solid rgba(255, 122, 61, 0.18);
  border-radius: 12px;
  padding: 12px 16px;
  box-shadow: inset 0 0 30px rgba(255, 122, 61, 0.03);
}
.card h3 { font-size: 14px; color: #FF9A5A; margin-bottom: 8px; }
.card h3::before {
  content: '';
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
  background: radial-gradient(circle at 35% 35%, #FFB98A, #FF7A3D);
  box-shadow: 0 0 8px rgba(255, 122, 61, 0.6);
}
.chart { height: calc(100% - 40px); }
.row-span-2 { grid-row: span 2; }
.col-span-2 { grid-column: span 2; }
.kpi-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 8px; }
.kpi { background: rgba(255, 122, 61, 0.08); border: 1px solid rgba(255, 122, 61, 0.12); border-radius: 8px; padding: 12px; text-align: center; }
.kpi-val { display: block; font-size: 32px; font-weight: 700; color: #FF7A3D; font-variant-numeric: tabular-nums; }
.kpi-label { display: block; font-size: 12px; color: #9C8A7C; margin-top: 4px; }
.scroll-list { overflow-y: auto; height: calc(100% - 40px); }
.order-item { padding: 8px 0; border-bottom: 1px solid rgba(255, 122, 61, 0.08); font-size: 13px; color: #C4B6A8; }
</style>
