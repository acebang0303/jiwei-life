import http from './http'
import type { EmployeeLoginDTO, LoginResult, Employee, PageResult, Order, Dish, Category, Setmeal, CouponTemplate, SeckillActivity } from '@/types'

// ========== 认证 ==========
export const loginApi = (data: EmployeeLoginDTO) =>
  http.post<LoginResult>('/employee/login', data)

export const logoutApi = () =>
  http.post('/employee/logout')

export const getCurrentEmployee = () =>
  http.get('/employee/info')

// ========== 员工管理 ==========
export const getEmployeePage = (params: { page: number; pageSize: number; name?: string }) =>
  http.get<PageResult<Employee>>('/employee/page', { params })

export const updateEmployeeStatus = (id: number, status: number) =>
  http.put(`/employee/${id}/status`, null, { params: { status } })

// ========== 订单 ==========
export const getOrderPage = (params: { page: number; pageSize: number; merchantId?: number; status?: number }) =>
  http.get<PageResult<Order>>('/order/page', { params })

export const confirmOrder = (id: number) =>
  http.put(`/order/${id}/confirm`)

export const rejectOrder = (id: number, reason?: string) =>
  http.put(`/order/${id}/reject`, null, { params: { reason } })

export const completeOrder = (id: number) =>
  http.put(`/order/${id}/complete`)

export const refundOrder = (id: number) =>
  http.post(`/order/${id}/refund`)

export const getOrderStatistics = () =>
  http.get('/order/statistics')

// ========== 商户 ==========
export const getMerchantPage = (params: any) =>
  http.get<PageResult<any>>('/merchant/page', { params })

export const auditMerchant = (id: number, status: number, auditReason?: string) =>
  http.put(`/merchant/${id}/audit`, null, { params: { status, auditReason } })

// ========== 分类 ==========
export const getCategoryList = (params?: { merchantId?: number; type?: number }) =>
  http.get<Category[]>('/category/list', { params })

export const createCategory = (data: Category) =>
  http.post('/category', data)

export const updateCategory = (id: number, data: Category) =>
  http.put(`/category/${id}`, data)

export const deleteCategory = (id: number) =>
  http.delete(`/category/${id}`)

// ========== 菜品 ==========
export const getDishPage = (params: any) =>
  http.get<PageResult<Dish>>('/dish/page', { params })

export const createDish = (data: Dish) =>
  http.post('/dish', data)

export const updateDish = (id: number, data: Dish) =>
  http.put(`/dish/${id}`, data)

export const updateDishStatus = (id: number, status: number) =>
  http.put(`/dish/${id}/status`, null, { params: { status } })

export const deleteDish = (ids: number[]) =>
  http.delete('/dish', { params: { ids } })

// ========== 套餐 ==========
export const getSetmealPage = (params: any) =>
  http.get<PageResult<Setmeal>>('/setmeal/page', { params })

export const createSetmeal = (data: Setmeal) =>
  http.post('/setmeal', data)

export const updateSetmeal = (id: number, data: Setmeal) =>
  http.put(`/setmeal/${id}`, data)

export const deleteSetmeal = (ids: number[]) =>
  http.delete('/setmeal', { params: { ids } })

// ========== 优惠券 ==========
export const getCouponTemplatePage = (params: any) =>
  http.get<PageResult<CouponTemplate>>('/coupon/template/page', { params })

export const createCouponTemplate = (data: CouponTemplate) =>
  http.post('/coupon/template', data)

export const updateCouponStatus = (id: number, status: number) =>
  http.put(`/coupon/template/${id}/status`, null, { params: { status } })

export const grantCoupon = (templateId: number, userIds: number[]) =>
  http.post('/coupon/grant', null, { params: { templateId, userIds } })

// ========== 秒杀 ==========
export const getSeckillPage = (params: any) =>
  http.get<PageResult<SeckillActivity>>('/seckill/page', { params })

export const createSeckill = (data: SeckillActivity) =>
  http.post('/seckill', data)

// ========== 内容审核 ==========
export const getReviewAuditPage = (params: any) =>
  http.get('/review/audit', { params })

export const auditReview = (id: number, auditStatus: number, auditReason?: string) =>
  http.put(`/review/${id}/audit`, null, { params: { auditStatus, auditReason } })

export const getNoteAuditPage = (params: any) =>
  http.get('/note/audit', { params })

export const auditNote = (id: number, auditStatus: number) =>
  http.put(`/note/${id}/audit`, null, { params: { auditStatus } })

// ========== 图片上传 ==========
export const uploadImage = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return http.post<{ url: string }>('/common/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
