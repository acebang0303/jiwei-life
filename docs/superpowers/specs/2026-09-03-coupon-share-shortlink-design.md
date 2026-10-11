# 优惠券分享短链 — 设计文档

**日期**：2026-09-03
**状态**：已确认设计

## 目标

让用户能把平台优惠券分享给好友领取，打通两条传播渠道，复用项目已有的短链技术栈。**无邀请返利**——分享的是「模板券领取链接」，被分享者领到同一模板的新券（各自独立，互不影响额度）。

## 需求（已与用户逐条确认）

1. **机制**：分享「模板券领取链接」。A 分享模板 T，B 点开领到模板 T 的新券（复用现有 `receive` 逻辑，受 `perUserLimit` / `totalCount` 约束）。无返利，无邀请关系表。
2. **双渠道**：
   - **微信原生分享**：小程序内 `onShareAppMessage`，B 在小程序内点卡片直达领券。
   - **短链分享**：A 生成短链（`s/xxx`）可发微信外任意处；B 点开落到 **admin 公开 H5 页**展示券信息，再扫码/识别进入小程序领券。
3. **短链落地**：admin（Vue3）加公开路由 `/coupon-share`，`meta.requiresAuth:false`。
4. **小程序码**：项目无正式 appid/secret，**开发用占位图**（带 templateId 文字），接口预留真 API 结构。

## 约束（技术调研结论）

- 短链基础设施已存在且完整：`ShortLinkService.create(longUrl)`（MurmurHash64+Base62+Caffeine→布隆→DB）、`POST /api/short-link/create`、`GET /s/{code}` 302。**零业务调用**，本次接入即复用。
- `CouponService.receive(templateId)` 已存在，B 领券走它即可，**无需改领取逻辑**。
- **admin 代理约定**：vite 把 `/api` rewrite 成 `/admin`；另有 `/user` 代理段不 rewrite。故公开接口**必须挂 `/user/**`** 才能被 admin 的 H5 页正常调用。
- **拦截器**：`WebMvcConfiguration` 拦 `/user/**`（JwtTokenUserInterceptor）与 `/admin/**`（AdminInterceptor）。仅公开只读路径 `/user/coupon-share/info` 与 `/user/coupon-share/minicode` 需在 User 拦截器 exclude 放行；`/user/coupon-share/link` 保留走 JWT（分享由登录用户发起）。`/api/**`、`/s/**` 本就不拦。
- 小程序 coupon 页、领券 API（`/user/coupon/receive/{id}`）已存在。

## 架构与数据流

### 双渠道数据流（收敛到同一终点：小程序 coupon 页定位模板 → 现有 receive）

```
渠道1·微信原生分享（小程序内闭环）：
A 在小程序 coupon 页点某券「分享」→ onShareAppMessage(path=/pages/coupon/coupon?templateId=5)
  → B 在聊天点卡片 → 小程序启动带参 → 定位到 coupon 页该模板(高亮) → B 登录领券(receive)

渠道2·短链分享（可发微信外）：
A 点「复制短链」→ 调 POST /user/coupon-share/link?templateId=5 → 后端拼 longUrl
  = <base-url>/coupon-share?templateId=5 → ShortLinkService.create → 返回 shortCode
  → A 复制 s/{code} 发微信/群/外部
  → B 点开 s/{code} → 302 → admin 公开页 /coupon-share?templateId=5
  → 该页调 GET /user/coupon-share/info?templateId=5 展示券信息
  → 展示小程序码(占位图，带 templateId) → B 长按/扫码 → 打开小程序 coupon 页定位模板 → 领券
```

### 关键设计点

- **短链承载**：短链的 longUrl 指向 `http://<host>/coupon-share?templateId={id}`。host 来自配置 `coupon.share.base-url`（开发 `http://localhost:5173`，生产换真域名），避免硬编码。
- **领券不变**：无论哪个渠道，B 最终在小程序 coupon 页点「领取」，走现有 `receive`。
- **无新增领取/返利逻辑**：不加邀请表、不加发放奖励。

## 组件设计

### 后端（jiwei-modules，market 域 @DS("market")）

**新增 `com.jiwei.market.couponshare.CouponShareController`**（`@RequestMapping("/user/coupon-share")`）

| 接口 | 路径 | 说明 |
|---|---|---|
| 生成分享短链 | `POST /user/coupon-share/link?templateId={id}` | 拼 longUrl（base-url + templateId）→ 调 `ShortLinkService.create` → 返回 `{shortCode, shortUrl}` |
| 券信息（公开） | `GET /user/coupon-share/info?templateId={id}` | 返回券 name/threshold/discountValue/validDays/status；模板不存在或禁用返回明确 code |
| 小程序码（占位） | `GET /user/coupon-share/minicode?templateId={id}` | 开发返回占位图（SVG/PNG 带 templateId 文字）；预留真实 `getwxacodeunlimit` 结构 |

**新增 `CouponShareService`**（薄封装）：查模板（只读，复用 CouponTemplateMapper）、拼 URL、调 ShortLinkService、生成占位码。

**修改 `WebMvcConfiguration`**：User 拦截器仅放行两个公开只读路径 `excludePathPatterns("/user/coupon-share/info", "/user/coupon-share/minicode")`（`/link` 保留走 JWT）。

**配置**：`coupon.share.base-url=http://localhost:5173`（application.yml，生产覆盖）。

### admin 前端（jiwei-admin）

- **路由** `src/router/index.ts` 加：`{ path: '/coupon-share', name: 'CouponShare', component: ..., meta: { title:'领券', requiresAuth: false } }`（独立于 MainLayout，仿 login 公开路由）
- **页面** `src/views/coupon/CouponShare.vue`：`onLoad/route` 读 `templateId` → 调 `GET /user/coupon-share/info`（axios 直发 `/user/...`，走 admin `/user` 代理不 rewrite）→ 展示券卡片 → 展示小程序码占位图 → 提示「微信扫码/识别打开小程序领取」
- api 封装：`src/api/index.ts` 加 `getCouponShareInfo(templateId)` 等

### 小程序（jiwei-miniapp）

- **coupon.vue**：`onLoad(options)` 读 `templateId` → 若存在则切到「领券中心」tab 并高亮滚动到该券；每张券卡片加「分享」入口
- **分享动作**：点分享 → 调 `POST /user/coupon-share/link` 生成短链 → 弹窗展示 shortUrl 供复制；同时页面 `onShareAppMessage` 返回 `{ path: '/pages/coupon/coupon?templateId=' + currentId, title: '送你一张券' }`
- **小程序启动带参**：coupon 是 tabBar 页，B 从分享卡片进入时 `path=/pages/coupon/coupon?templateId=X` 作为冷启动场景，query 在 `App.vue onLaunch(options.query)` 中。实现：`App.vue onLaunch` 若 `options.query.templateId` 存在则写入全局（store/storage `pendingCouponTemplateId`），coupon 页 `onShow` 读取并消费该值 → 高亮定位；热启动（小程序已在前台）走页面 `onLoad` 或 `onShow` 透传兜底。
- api：`src/api/index.js` 加 `getCouponShareLink(templateId)`

## 错误处理与边界

- `info` 接口：templateId 无效 → code 失败 + 提示「券不存在」；模板 status=0 禁用 → 提示「活动已结束」
- `link` 接口：生成短链失败 → 复用 ShortLinkService 的「短码生成失败请重试」异常
- admin 公开页：无 token 可访问；H5 不在微信内打开时小程序码无法识别，提示用户复制短链自行打开小程序 coupon 页搜索该券（H5 同时展示 templateId 对应券名，用户可在领券中心找到）
- coupon.vue 带参进入：参数缺失时走默认行为（不崩）

## 测试

- 后端：CouponShareService 单测（info 查模板、link 拼 URL + 调 ShortLinkService mock、占位码生成）；CouponShareController 接口冒烟
- admin：公开路由无 token 可访问
- 小程序：coupon 页带 templateId 定位不崩、分享按钮触发
- 复用项目现有 JUnit5+Mockito 风格（不启 Spring 上下文）

## 明确不做（YAGNI）

- 邀请返利/成长值奖励/邀请关系表
- 真实微信小程序码 API（无 appid，占位即可）
- H5 内直接领券（H5 无登录态，领券必须在小程序）
- 微信 URL Scheme / URL Link 跳转
