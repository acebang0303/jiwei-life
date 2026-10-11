// ========== 通用类型定义 ==========

export interface Result<T> {
  code: number
  msg: string | null
  data: T
}

export interface PageResult<T> {
  total: number
  records: T[]
}

// ========== 认证 ==========
export interface EmployeeLoginDTO {
  username: string
  password: string
}

export interface Employee {
  id: number
  username: string
  name: string
  phone: string
  roleId: number
  status: number
  createTime: string
}

export interface LoginResult {
  id: number
  name: string
  username: string
  token: string
}

// ========== 订单 ==========
export interface Order {
  id: number
  number: string
  userId: number
  merchantId: number
  payAmount: number
  status: number
  payStatus: number
  addressDetail: string
  remark: string
  createTime: string
  payTime: string
}

// ========== 商户 ==========
export interface MerchantInfo {
  id: number
  name: string
  logo: string
  description: string
  address: string
  phone: string
  businessHours: string
  status: number
  auditReason: string
  createTime: string
}

// ========== 分类 ==========
export interface Category {
  id: number
  merchantId: number
  type: number
  name: string
  sort: number
  status: number
}

// ========== 菜品 ==========
export interface Dish {
  id: number
  merchantId: number
  categoryId: number
  name: string
  image: string
  description: string
  price: number
  status: number
  sales: number
  flavors: FlavorDTO[]
  createTime: string
}

export interface FlavorDTO {
  name: string
  value: string
}

// ========== 套餐 ==========
export interface Setmeal {
  id: number
  merchantId: number
  categoryId: number
  name: string
  image: string
  description: string
  price: number
  status: number
  createTime: string
}

// ========== 营销 ==========
export interface CouponTemplate {
  id: number
  name: string
  type: number
  threshold: number
  discountValue: number
  totalCount: number
  perUserLimit: number
  validDays: number
  status: number
}

export interface SeckillActivity {
  id: number
  name: string
  dishId: number
  seckillPrice: number
  stock: number
  limitPerUser: number
  startTime: string
  endTime: string
  status: number
}
