# 优惠券分享短链实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 打通优惠券「模板领取链接」的微信原生分享 + 短链分享双渠道，复用现有短链技术与领券逻辑。

**Architecture:** 后端在 market 域新增 `coupon-share` 业务：生成分享短链（内部调 `ShortLinkService.create`）、提供公开券信息接口、返回开发占位小程序码。admin 前端加公开落地页 `/coupon-share`（展示券 + 占位码，引导进小程序领券）。小程序 coupon 页加分享按钮 + `onShareAppMessage` + 接收 `templateId` 定位高亮。领券本身复用现有 `CouponService.receive`，不改。

**Tech Stack:** Spring Boot / MyBatis-Plus / dynamic-datasource(@DS market) / Vue3 / uni-app / 现有 ShortLinkService(MurmurHash+Base62)

**Spec:** `docs/superpowers/specs/2026-09-03-coupon-share-shortlink-design.md`

## Global Constraints

- 后端根 `jiwei-life/jiwei-modules`，admin 根 `jiwei-life/jiwei-admin`，小程序根 `jiwei-life/jiwei-miniapp`
- market 域 Service/Mapper 加 `@DS("market")`；新 CouponShare 相关类放 `com.jiwei.market.couponshare` 包
- 短链服务 `ShortLinkService.create(String longUrl)` 在 `com.jiwei.market.shortlink.service`，构造注入；返回 shortCode
- 券模板实体 `CouponTemplate`（`com.jiwei.pojo.entity`）字段：id/name/type(1满减2折扣3现金)/threshold/discountValue/totalCount/perUserLimit/validDays/status/createTime
- 领券：`POST /user/coupon/receive/{templateId}` 走登录态 `BaseContext`；**不改 receive**
- 拦截器：`WebMvcConfiguration` 的 JwtTokenUserInterceptor 拦 `/user/**`；需 exclude `/user/coupon-share/**`
- admin vite 代理：`/api`→rewrite→`/admin`，`/user`→8080 **不 rewrite**；故公开落地页请求须以 `/user/...` 开头（不经过管理端 http 实例的 `/api` baseURL）
- 小程序 coupon.vue 是 `<script setup>` + tabBar 页，api 在 `src/api/index.js`
- 改 pojo/framework 后需先 install（本计划不改 pojo/framework，仅 modules/admin/miniapp）

---

### Task 1: 后端 CouponShareService + Controller + 拦截器放行

**Files:**
- Create: `jiwei-modules/src/main/java/com/jiwei/market/couponshare/service/CouponShareService.java`
- Create: `jiwei-modules/src/main/java/com/jiwei/market/couponshare/service/impl/CouponShareServiceImpl.java`
- Create: `jiwei-modules/src/main/java/com/jiwei/market/couponshare/controller/CouponShareController.java`
- Modify: `jiwei-framework/src/main/java/com/jiwei/framework/config/WebMvcConfiguration.java`（加 exclude）

**Interfaces:**
- Consumes: `ShortLinkService`（com.jiwei.market.shortlink.service）、`CouponTemplateMapper`（com.jiwei.market.mapper）、`@DS("market")`
- Produces: `String createShareLink(Long templateId)` 返回短码；`CouponTemplate getShareInfo(Long templateId)`；`String generateMiniCode(Long templateId)` 返回占位图 URL 或 dataURL

- [ ] **Step 1: 写 CouponShareService 接口**

```java
package com.jiwei.market.couponshare.service;

import com.jiwei.pojo.entity.CouponTemplate;

public interface CouponShareService {
    /** 生成分享短链短码（longUrl = {base-url}/coupon-share?templateId={id}） */
    String createShareLink(Long templateId);
    /** 查券信息供 H5 公开展示；不存在/禁用返回 null 由调用方给提示 */
    CouponTemplate getShareInfo(Long templateId);
    /** 生成开发占位小程序码（带 templateId 文字的 SVG dataURL） */
    String generateMiniCode(Long templateId);
}
```

- [ ] **Step 2: 写实现类**

```java
package com.jiwei.market.couponshare.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.jiwei.market.couponshare.service.CouponShareService;
import com.jiwei.market.mapper.CouponTemplateMapper;
import com.jiwei.market.shortlink.service.ShortLinkService;
import com.jiwei.pojo.entity.CouponTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@DS("market")
@Service
@Slf4j
public class CouponShareServiceImpl implements CouponShareService {

    private final CouponTemplateMapper couponTemplateMapper;
    private final ShortLinkService shortLinkService;

    /** H5 落地页基础地址，dev=http://localhost:5173，生产用真域名覆盖 */
    @Value("${coupon.share.base-url:http://localhost:5173}")
    private String baseUrl;

    public CouponShareServiceImpl(CouponTemplateMapper couponTemplateMapper,
                                  ShortLinkService shortLinkService) {
        this.couponTemplateMapper = couponTemplateMapper;
        this.shortLinkService = shortLinkService;
    }

    @Override
    public String createShareLink(Long templateId) {
        String longUrl = baseUrl + "/coupon-share?templateId=" + templateId;
        return shortLinkService.create(longUrl);
    }

    @Override
    public CouponTemplate getShareInfo(Long templateId) {
        return couponTemplateMapper.selectById(templateId);
    }

    @Override
    public String generateMiniCode(Long templateId) {
        // 开发占位：真实实现应调微信 getwxacodeunlimit 返回小程序码图
        // 此处生成一张带 templateId 文字的简单 SVG dataURL，占位可展示
        String text = "coupon-template-" + templateId;
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='200' height='200'>"
                + "<rect width='200' height='200' fill='#FF7A3D'/>"
                + "<text x='100' y='105' font-size='14' fill='white' text-anchor='middle'>"
                + text + "</text></svg>";
        return "data:image/svg+xml;utf8," + java.net.URLEncoder.encode(svg, java.nio.charset.StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 3: 写 Controller（三接口）**

```java
package com.jiwei.market.couponshare.controller;

import com.jiwei.common.exception.BaseException;
import com.jiwei.common.result.Result;
import com.jiwei.market.couponshare.service.CouponShareService;
import com.jiwei.pojo.entity.CouponTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user/coupon-share")
@Slf4j
@Tag(name = "优惠券分享短链（公开）")
public class CouponShareController {

    private final CouponShareService couponShareService;

    public CouponShareController(CouponShareService couponShareService) {
        this.couponShareService = couponShareService;
    }

    @PostMapping("/link")
    @Operation(summary = "生成分享短链")
    public Result<Map<String, String>> link(@RequestParam Long templateId) {
        String code = couponShareService.createShareLink(templateId);
        Map<String, String> data = new HashMap<>();
        data.put("shortCode", code);
        data.put("shortUrl", "/s/" + code);
        return Result.success(data);
    }

    @GetMapping("/info")
    @Operation(summary = "券信息（H5 公开展示）")
    public Result<CouponTemplate> info(@RequestParam Long templateId) {
        CouponTemplate template = couponShareService.getShareInfo(templateId);
        if (template == null) {
            throw new BaseException("优惠券不存在");
        }
        if (template.getStatus() != null && template.getStatus() == 0) {
            throw new BaseException("该优惠券活动已结束");
        }
        return Result.success(template);
    }

    @GetMapping("/minicode")
    @Operation(summary = "小程序码（开发占位）")
    public Result<Map<String, String>> minicode(@RequestParam Long templateId) {
        String dataUrl = couponShareService.generateMiniCode(templateId);
        Map<String, String> data = new HashMap<>();
        data.put("image", dataUrl);
        return Result.success(data);
    }
}
```

- [ ] **Step 4: WebMvcConfiguration 放行公开路径**

Modify `jiwei-framework/src/main/java/com/jiwei/framework/config/WebMvcConfiguration.java` 的 user 拦截器 exclude 列表（约 L43-45 附近）加一行：
```java
.excludePathPatterns("/user/coupon-share/**")
```

- [ ] **Step 5: 编译**

Run: `cd /d/software/savorylife/jiwei-life && mvn compile -pl jiwei-framework,jiwei-modules -am -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 6: 验证接口（起后端后）**
可选：`mvn install` 后 spring-boot:run，用 curl 打 `/user/coupon-share/info?templateId=1`（需后端跑着 DB）
Expected: 返回券模板 JSON

- [ ] **Step 7: Commit**

```bash
git add jiwei-modules/src/main/java/com/jiwei/market/couponshare \
        jiwei-framework/src/main/java/com/jiwei/framework/config/WebMvcConfiguration.java
git commit -m "feat(coupon): add coupon-share public endpoints + interceptor exclusion"
```

---

### Task 2: admin 公开落地页 /coupon-share

**Files:**
- Create: `jiwei-admin/src/views/coupon/CouponShare.vue`
- Modify: `jiwei-admin/src/router/index.ts`
- Create(可选): `jiwei-admin/src/api/couponShare.ts`

**Interfaces:**
- Consumes: 后端 `GET /user/coupon-share/info?templateId=`, `GET /user/coupon-share/minicode?templateId=`
- Produces: 公开路由 `/coupon-share`（requiresAuth:false），独立于 MainLayout

- [ ] **Step 1: 建公开 API 模块（不经管理端 http 的 /api rewrite）**

Create `jiwei-admin/src/api/couponShare.ts`：
```ts
import axios from 'axios'

// 公开接口走 /user 前缀（vite 代理 /user → 8080 不 rewrite），不注入管理端 token
export const getCouponShareInfo = (templateId: number) =>
  axios.get(`/user/coupon-share/info`, { params: { templateId } }).then(r => r.data?.data)

export const getCouponShareMiniCode = (templateId: number) =>
  axios.get(`/user/coupon-share/minicode`, { params: { templateId } }).then(r => r.data?.data)
```

- [ ] **Step 2: 写 CouponShare.vue**

Create `jiwei-admin/src/views/coupon/CouponShare.vue`：
```vue
<template>
  <div class="coupon-share-page">
    <div v-if="loading">加载中...</div>
    <div v-else-if="error">{{ error }}</div>
    <div v-else class="coupon-card">
      <h2>{{ template.name }}</h2>
      <p class="value">{{ couponText }}</p>
      <p class="threshold">{{ thresholdText }}</p>
      <p class="valid">领取后 {{ template.validDays }} 天内有效</p>
      <img :src="miniCode.image" class="mini-code" alt="小程序码" />
      <p class="hint">微信扫码 / 长按识别打开小程序即可领取</p>
      <p class="hint2">或在小程序中打开「领券中心」搜索「{{ template.name }}」</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getCouponShareInfo, getCouponShareMiniCode } from '@/api/couponShare'
import type { CouponTemplate } from '@/types'

const route = useRoute()
const templateId = Number(route.query.templateId)
const loading = ref(true)
const error = ref('')
const template = ref<CouponTemplate | null>(null)
const miniCode = ref<{ image: string }>({ image: '' })

const couponText = computed(() => {
  const t = template.value
  if (!t) return ''
  if (t.type === 2) return (Number(t.discountValue) * 10) + '折'
  return '¥' + t.discountValue
})
const thresholdText = computed(() => {
  const t = template.value
  if (!t) return ''
  if (t.type === 3) return '无门槛'
  return '满' + t.threshold + '可用'
})

onMounted(async () => {
  if (!templateId) { error.value = '链接无效'; loading.value = false; return }
  try {
    template.value = await getCouponShareInfo(templateId)
    miniCode.value = await getCouponShareMiniCode(templateId)
  } catch (e: any) {
    error.value = e?.response?.data?.msg || '券信息加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.coupon-share-page { min-height: 100vh; background: #fff; display: flex; align-items: center; justify-content: center; padding: 24px; }
.coupon-card { text-align: center; border: 2px dashed #FF7A3D; border-radius: 12px; padding: 32px; max-width: 420px; }
.value { font-size: 42px; color: #FF7A3D; font-weight: bold; }
.threshold { color: #999; }
.mini-code { width: 180px; height: 180px; margin-top: 16px; }
.hint { color: #666; margin-top: 12px; font-size: 14px; }
.hint2 { color: #999; margin-top: 6px; font-size: 13px; }
</style>
```

- [ ] **Step 3: 注册公开路由（独立于 MainLayout，仿 login）**

Modify `jiwei-admin/src/router/index.ts`，在 `/login` 路由后加：
```ts
{
  path: '/coupon-share',
  name: 'CouponShare',
  component: () => import('@/views/coupon/CouponShare.vue'),
  meta: { title: '优惠券领取', requiresAuth: false }
},
```

- [ ] **Step 4: 类型检查**

Run: `cd /d/software/savorylife/jiwei-life/jiwei-admin && npx vue-tsc --noEmit 2>&1 | tail -10`
Expected: 无新增错误（`CouponTemplate` 类型若缺 type 字段报错则补）

- [ ] **Step 5: Commit**

```bash
git add jiwei-admin/src/views/coupon/CouponShare.vue \
        jiwei-admin/src/router/index.ts \
        jiwei-admin/src/api/couponShare.ts
git commit -m "feat(admin): public coupon-share landing page"
```

---

### Task 3: 小程序 coupon 页分享 + 带参定位

**Files:**
- Modify: `jiwei-miniapp/src/pages/coupon/coupon.vue`
- Modify: `jiwei-miniapp/src/App.vue`（onLaunch 读 templateId 存 store/storage）
- Modify: `jiwei-miniapp/src/api/index.js`（加 createShareLink）

**Interfaces:**
- Consumes: 后端 `POST /user/coupon-share/link?templateId=`（需登录态，走现有 request）
- Produces: coupon 页支持 `templateId` 定位高亮 + 每张券「分享」按钮 + onShareAppMessage

- [ ] **Step 1: api 加分享短链方法**

Modify `jiwei-miniapp/src/api/index.js`，在 coupon 区加：
```js
// 生成优惠券分享短链
export const createCouponShareLink = (templateId) => request({
  url: '/user/coupon-share/link',
  method: 'POST',
  data: { templateId }
})
```
并加入底部 export 对象。

- [ ] **Step 2: App.vue onLaunch/onShow 处理分享带参**

Modify `jiwei-miniapp/src/App.vue`（当前是 `<script setup>`，`import { onLaunch } from '@dcloudio/uni-app'`）：
```js
<script setup>
import { onLaunch, onShow } from '@dcloudio/uni-app'

// 从分享卡片进入时，query.templateId 写入 storage 供 coupon 页定位
const stashTemplateId = (options) => {
  const tid = options?.query?.templateId
  if (tid) {
    uni.setStorageSync('pendingCouponTemplateId', Number(tid))
  }
}

// 冷启动（小程序被分享卡片拉起）
onLaunch((options) => {
  stashTemplateId(options)
})

// 热启动（小程序已在后台，从分享卡片回到前台）
onShow((options) => {
  stashTemplateId(options)
})
</script>
```

- [ ] **Step 3: coupon.vue 加「分享」按钮 + onShareAppMessage + 带参定位**

Modify `jiwei-miniapp/src/pages/coupon/coupon.vue`：

template：在每张券卡片的 receive-wrap 上方/旁加分享入口：
```html
<view class="coupon-card" v-for="t in templates" :key="t.id">
  <!-- 现有 left/info 保留 -->
  <view class="receive-wrap">
    <text class="share-link" @click="shareTemplate(t)">分享</text>
    <!-- 现有 receive-btn 保留 -->
  </view>
</view>
```

script setup 完整改动（替换现有 import 与逻辑区）：
```js
<script setup>
import { ref, onMounted, onShow } from 'vue'
import { onShareAppMessage } from '@dcloudio/uni-app'
import { getCouponTemplates, getUserCouponList, receiveCoupon, createCouponShareLink } from '@/api/index.js'
import { useUserStore } from '@/store/user.js'

const userStore = useUserStore()
const tab = ref('receive')
const templates = ref([])
const myCoupons = ref([])
const receiving = ref(false)
const highlightId = ref(null)

// --- 原有 switchTab/loadTemplates/loadMyCoupons/receive/couponText/thresholdText/formatDate 保留 ---

// 生成分享短链弹窗（复制 or 引导右上角分享）
const shareTemplate = async (t) => {
  if (!userStore.isLogin) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  highlightId.value = t.id
  uni.showModal({
    title: '分享优惠券',
    content: '点击「复制短链」发给好友，或点右上角···分享给微信好友',
    confirmText: '复制短链',
    success: async (r) => {
      if (!r.confirm) return
      uni.showLoading({ title: '生成中' })
      try {
        const res = await createCouponShareLink(t.id)
        const code = res?.shortCode || ''
        uni.hideLoading()
        if (code) uni.setClipboardData({ data: '/s/' + code, success: () => uni.showToast({ title: '已复制短链', icon: 'success' }) })
      } catch (e) {
        uni.hideLoading()
        uni.showToast({ title: e.message || '生成失败', icon: 'none' })
      }
    }
  })
}

// 消费 App 层写入的 pendingCouponTemplateId，切到领券中心并高亮该券
const applyPendingTemplate = () => {
  const tid = uni.getStorageSync('pendingCouponTemplateId')
  if (!tid) return
  uni.removeStorageSync('pendingCouponTemplateId')
  highlightId.value = Number(tid)
  if (tab.value !== 'receive') switchTab('receive')
}

// 右上角分享：转发当前高亮券（B 收到后点开 → path 带 templateId）
onShareAppMessage(() => ({
  title: '送你一张优惠券，快来领取',
  path: '/pages/coupon/coupon' + (highlightId.value ? '?templateId=' + highlightId.value : '')
}))

onShow(applyPendingTemplate)
onMounted(loadTemplates)
</script>
```

template：每张券卡片的 receive-wrap 内、receive-btn 前加分享，并给卡片加高亮 class：
```html
<view class="coupon-card" :class="{ highlight: highlightId === t.id }" v-for="t in templates" :key="t.id">
  <!-- left/info 保留 -->
  <view class="receive-wrap">
    <text class="share-link" @click="shareTemplate(t)">分享</text>
    <text class="received-info" v-if="t.receivedCount > 0">已领 {{ t.receivedCount }}/{{ t.perUserLimit }}</text>
    <button class="receive-btn" ...>...</button>
  </view>
</view>
```

style 加：
```scss
.coupon-card.highlight { border: 3rpx solid $warning-color; box-shadow: 0 4rpx 16rpx rgba(232,161,60,.3); }
.share-link { font-size: 22rpx; color: $warning-color; display: block; margin-bottom: 8rpx; text-decoration: underline; }
```

- [ ] **Step 4: 构建验证**

Run: `cd /d/software/savorylife/jiwei-life/jiwei-miniapp && npm run build:mp-weixin 2>&1 | tail -5`
Expected: DONE Build complete

- [ ] **Step 5: Commit**

```bash
git add jiwei-miniapp/src/pages/coupon/coupon.vue \
        jiwei-miniapp/src/App.vue \
        jiwei-miniapp/src/api/index.js
git commit -m "feat(miniapp): coupon share button + share card param landing"
```

---

### Task 4: 端到端验证与收尾

- [ ] **Step 1: install + 起后端**

Run:
```bash
cd /d/software/savorylife/jiwei-life && mvn install -pl jiwei-framework,jiwei-modules -am -DskipTests
# 起后端（确保 MySQL/Redis 在跑）
cd jiwei-modules && mvn spring-boot:run
```

- [ ] **Step 2: 验证公开接口**

Run:
```bash
curl "http://localhost:8080/user/coupon-share/info?templateId=1"
curl -X POST "http://localhost:8080/user/coupon-share/link?templateId=1"   # 无 token 应 401（该接口走登录）
```
Expected: info 公开返回券 JSON；link 需登录（符合预期，分享由登录用户发起）
> 注：若希望 link 也允许未登录生成，改为 exclude 它——但分享者必是小程序登录用户，保留登录合理。

- [ ] **Step 3: 验证 admin 落地页**

Run: admin dev 起后浏览器开 `http://localhost:5173/coupon-share?templateId=1`
Expected: 展示券信息 + 占位小程序码图

- [ ] **Step 4: 验证小程序**
微信开发者工具导入，coupon 页每券有「分享」，点后弹窗复制短链；分享卡片 path 带 templateId。
Expected: 无报错

- [ ] **Step 5: 记录运行注意（若 docker 未起则跳过，注明）**

- [ ] **Step 6: Commit（若有收尾改动）**

```bash
git add -A && git commit -m "chore: verify coupon-share end-to-end"
```

---

## 自审记录（执行者忽略）
- 覆盖度：spec 三接口 + admin 落地页 + 小程序分享/定位全覆盖。
- 无占位符：各文件代码已写全。
- 一致：createShareLink 返回 shortCode、info 返回 CouponTemplate、minicode 返回 image dataURL，三端（controller/admin/miniapp）字段对齐。
- 注意：`coupon.share.base-url` 配置在 impl 用 @Value，dev 默认 localhost:5173；生产覆盖。
