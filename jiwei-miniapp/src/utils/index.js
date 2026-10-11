// 订单状态映射
export const orderStatusMap = {
  1: { text: '待支付', color: '#FF7A3D' },
  2: { text: '待接单', color: '#E8A13C' },
  3: { text: '备货中', color: '#5B8DB8' },
  4: { text: '待取餐', color: '#4C9A6A' },
  5: { text: '已完成', color: '#3E7A53' },
  6: { text: '已取消', color: '#C4B6A8' },
  7: { text: '已退款', color: '#C4B6A8' }
}

// 支付状态映射
export const payStatusMap = {
  0: '未支付',
  1: '已支付',
  2: '已退款'
}

// 格式化时间
// iOS 的 JS 引擎不支持 new Date("yyyy-MM-dd HH:mm:ss")，需把空格换成 T（ISO 8601）
export const formatTime = (dateStr) => {
  if (!dateStr) return ''
  let d = new Date(dateStr)
  if (isNaN(d.getTime()) && typeof dateStr === 'string' && dateStr.includes(' ')) {
    d = new Date(dateStr.replace(' ', 'T'))
  }
  if (isNaN(d.getTime())) return ''
  const y = d.getFullYear()
  const M = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const h = String(d.getHours()).padStart(2, '0')
  const m = String(d.getMinutes()).padStart(2, '0')
  return y + '-' + M + '-' + day + ' ' + h + ':' + m
}

// 格式化价格
export const formatPrice = (price) => {
  return Number(price).toFixed(2)
}
