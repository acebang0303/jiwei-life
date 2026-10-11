# 吉味生活 · 小程序 8 个 Bug 修复提示词

> 用途：仅供粘贴给其它 AI 工具（ChatGPT / Claude / 其它代码助手）直接修 bug。
> 每个提示词都是**自包含**的：描述了现象 + 根因定位（精确到文件与行号）+ 期望行为 + 修复方向。
> 项目根目录：`jiwei-life`（后端 `jiwei-modules` / `jiwei-ai`，小程序 `jiwei-miniapp`，uni-app）。

---

## Bug 1｜成长值不随签到 / 下单 / 发布笔记增加

**现象**：「我的」页成长值恒为 0，等级恒为 Lv.1。签到提示「+5 成长值」但实际不变，下单、发笔记后也不变。

**根因**：`user` 表只有 `growth_value` / `level` 两个普通整型列，全项目**没有任何一处对它们做自增**。仅建号时初始化一次 `UserAuthServiceImpl.findOrRegister()`（`jiwei-modules/src/main/java/com/jiwei/auth/service/impl/UserAuthServiceImpl.java:76-77`，置 0 / 1）。而三个行为动作后端都没有写成长值：

- 签到：`JiweiModules/.../market/service/impl/SignServiceImpl.java:23-38` `sign()` 只 `redisTemplate.opsForValue().setBit(...)`，未 `growthValue += 5`。
- 下单支付成功：`JiweiModules/.../trade/mq/OrderPaidConsumer.java:60-70` 只回写 `pay_status/status` 并推送商家提醒，未加成长值。（来源 `PayOrderServiceImpl.markOrderPaid()` `trade/pay/service/impl/PayOrderServiceImpl.java:166-172`）
- 发布笔记：`JiweiModules/.../social/service/impl/NoteServiceImpl.java:76-97` `publish()` 只 `insert` + 发向量同步消息，未加成长值。

查询接口没问题：`UserProfileController.growth()`（`.../user/controller/UserProfileController.java:104-115`）读 `user.growth_value` 如实返回 0。前端 `profile.vue`（`jiwei-miniapp/src/pages/profile/profile.vue:206-211`）`loadGrowth()` 也正确，显示 0 是数据真实的反映。

**修复提示词（直接复制给 AI）**：

```text
你是后端 Java 工程师，请修复「成长值不随签到/下单/发布笔记增加」的问题。项目是 Spring Boot 分模块单体，根目录 jiwei-life。

背景：user 表的 growth_value / level 两个列在建号时被 UserAuthServiceImpl.findOrRegister()（jiwei-modules/src/main/java/com/jiwei/auth/service/impl/UserAuthServiceImpl.java:76-77）初始化为 0 和 1 后，全项目再无自增。需要新增一个「成长值累加 + 等级升级」的统一服务，并在三个行为回调解调用：

1. 签到：JiweiModules/src/main/java/com/jiwei/market/service/impl/SignServiceImpl.java 的 sign()（约 23-38 行）。它目前只写 Redis BitMap。请在签到成功（含防重复签到通过）后累加成长值 +5。
2. 下单支付成功：JiweiModules/src/main/java/com/jiwei/trade/mq/OrderPaidConsumer.java 的消费方法（约 60-70 行）。在订单回写 PAID 成功后，按订单金额或固定分值累加成长值。
3. 发布笔记：JiweiModules/src/main/java/com/jiwei/social/service/impl/NoteServiceImpl.java 的 publish()（约 76-97 行）。在 noteMapper.insert 成功后累加成长值。

要求：
- 新增一个 GrowthService（impl 可在 com.jiwei.user 或 com.jiwei.common 下），提供 addGrowth(userId, delta) 与查询 level 的方法。用 userMapper 读当前 user，growthValue += delta，并按设计的等级分档（如 Lv1=0、Lv2=100、Lv3=300、Lv4=600、Lv5=1000、Lv6=1500，请读代码或 db/02_user.sql 确认注释的分档表）计算出新 level 一并 update。
- 不要改查询/显示接口：/user/profile/growth（UserProfileController.growth）已经能如实返回，问题只在写入，请勿动前端 profile.vue。
- 三处调用点要在各自的事务内完成后 addGrowth；注意签到有「今日已签到」的判重，重复签到不应累加。
- 保持与现有代码风格一致（BaseContext.getCurrentId() 获取 userId），改动要最小、无多余抽象。修复后请自查这三处是否都真正调用了累加服务。
```

---

## Bug 2｜收货地址「地区」选择组件缺失 + 无法保存

**现象**：新增/编辑地址页，「地区」栏没有省市区选择器；点「保存」可能失败或地区为空。

**根因**：地区**已实现** `picker mode="region"`，不是没组件。问题在两处：

- 前端 `jiwei-miniapp/src/pages/address/edit.vue:12-17` 用 `<picker mode="region">`，`onRegion`（41-47 行）只取名称 `e.detail.value[0..2]`，**丢弃了 `e.detail.code`**（省/市/区编码），导致后端 `provinceCode/cityCode/districtCode` 永远为 null。
- `save()`（edit.vue:65-84）第 66 行只校验 `consignee/phone/detail`，**未校验地区必填**。
- 「保存失败」多是**未登录**：后端 `JwtTokenUserInterceptor`（jiwei-framework/.../interceptor/JwtTokenUserInterceptor.java:34-42）对所有 `/user/**` 强制鉴权，无 token 返回 401；前端 `request` 用 `silent:true` 静默吞掉。鉴权通过后字段名 `consignee/phone/provinceName/cityName/districtName/detail/label` 前后端一致（`AddressBook.java` 实体 vs `edit.vue` 37 行），保存本身能成功。

**修复提示词（直接复制给 AI）**：

```text
你是 uni-app 微信小程序 + Spring Boot 后端工程师，请修复「收货地址地区选择不全 / 保存失败」的问题。项目根目录 jiwei-life。

修复点（jiwei-miniapp/src/pages/address/edit.vue）：
1. onRegion（约 41-47 行）：picker mode="region" 的 change 事件里 e.detail 有 value 数组和 code 数组。当前只把 value[0/1/2] 赋给 provinceName/cityName/districtName。请同时把 e.detail.code[0/1/2] 赋给 form.provinceCode/cityCode/districtCode，并在表单初始 state（约 37 行）补齐这 3 个 code 字段。
2. save() 校验（约 65-66 行）：在现有「请填写完整信息」校验里加上「未选择地区」的校验：若 provinceName 为空则 toast 提示「请选择省市区」并 return。
3. 若你发现保存仍失败，大概率不是本页问题，而是登录态：后端 JwtTokenUserInterceptor 对所有 /user/** 强制鉴权（jiwei-framework/src/main/java/com/jiwei/framework/interceptor/JwtTokenUserInterceptor.java:38-42 无 token 返回 401）。请确认前端请求是否带上了 token（jiwei-miniapp/src/api/index.js 的请求拦截器是否从存储读取 token 注入 header）。不要改动后端鉴权逻辑本身。

保持改动最小，只动 edit.vue 与（必要时）请求拦截器，不重构其它页面。
```

---

## Bug 3｜购物车存不进 Redis，进购物车页一直空

**现象**：购物车 tab 页始终「购物车空空如也」，即使从店铺页加过菜。

**根因（主要在前端）**：`jiwei-miniapp/src/pages/cart/cart.vue` 的 `<script setup>`（46-73 行）只 `useCartStore()`，**没有 `onMounted`/`onShow`，从不调用 `cartStore.fetchCart()`**。它直接读 Pinia store 的初始空数组 `items: []`（`jiwei-miniapp/src/store/cart.js:5-9`），所以冷启动/切 tab/未在店铺页加过车时永远空。对比：店铺页 `shop.vue:324-343` 在 `onMounted` 里调了 `fetchCart()`。

后端本身正常：`ShoppingCartServiceImpl` 用 Redis Hash 存（`cart:{userId}`，value 为 JSON 字符串），`RedisConfiguration` 已用 `GenericJackson2JsonRedisSerializer`，不是 JDK 序列化问题，字段名前后端一致。

**修复提示词（直接复制给 AI）**：

```text
你是 uni-app 微信小程序工程师，请修复「购物车页始终显示空」问题。项目根目录 jiwei-life。

根因：jiwei-miniapp/src/pages/cart/cart.vue 的 script setup（约 46-73 行）没有 onMounted/onShow，从未调用 cartStore.fetchCart()，页面只读 store 初始值 items: []。后端 Redis 存取链路（ShoppingCartServiceImpl 用 cart:{userId} Hash，RedisConfiguration 用 JSON 序列化器）是正常的，勿改动后端。

修复：
1. 在 cart.vue 引入 vue 的 onShow（uni-app tab 页用 onShow），并调用 cartStore.fetchCart() 拉取购物车（fetchCart 内部调 /user/cart/list）。建议 onShow 每次返回都刷新一次。
2. 确认 store/cart.js 的 fetchCart()（约 12-20 行）能从后端拿到数据并回写 this.items；若返回结构是 {items, totalPrice} 之类，请核对字段名（购物车页模板用的是 cartStore.items.length、cartStore.totalPrice）。
3. 空态与列表态切换逻辑（v-if="cartStore.items.length > 0"）无需改，确保拉取后 items 被赋值即可。

改动仅限 cart.vue（和必要时 store/cart.js），最小修改，不触碰后端商品/购物车逻辑。
```

---

## Bug 4｜笔记详情无法加载（或没做）

**现象**：笔记详情页只显示作者头部与空评论，正文/图片不渲染，像没实现。

**根因**：详情页与后端接口**都已实现**，不是空壳。真正的坑：

1. **抽鉴权 401（最可能是「打不开」的原因）**：后端 `WebMvcConfiguration`（jiwei-framework/.../config/WebMvcConfiguration.java:41-45）拦截所有 `/user/**`，`JwtTokenUserInterceptor` 无 token 时 401。前端 `getNoteDetail`（jiwei-miniapp/src/api/index.js:250-253）用 `silent:true`，401 被 `api/index.js:22-25` 静默 reject，页面只显示空壳。
2. **`images` 数据缺失**：`note` 表有 `images` 列（`db/06_social.sql:34`），但种子数据 `db/99_seed_data.sql:212-216` 与 mock 生成器 `db/generate_mock_data.mjs:415` 的 INSERT **都不含 images 列**，导致所有笔记 images 为 NULL，前端 `detail.vue:24` `v-if="note.images"` 永不成立，图片区永不渲染。
3. **二级回复不渲染（次要）**：后端 `NoteServiceImpl.detail()`（social/.../impl/NoteServiceImpl.java:219-241）只返回扁平评论列表，不构建 `children` 评论树；前端 `detail.vue:46` 却按 `c.children` 渲染回复，所以回复永远不显示。

后端接口 `UserNoteController`（`.../social/controller/user/UserNoteController.java:138-142`）详情返回 Note（含 title/content/images/merchantId/topicTags/likeCount/collectCount/viewCount 及 fillUserInfo 的昵称头像 isLiked 等）与评论列表，字段齐全。

**修复提示词（直接复制给 AI）**：

```text
你是 uni-app 微信小程序 + Spring Boot 后端工程师，请修复「笔记详情页内容不渲染」问题。项目根目录 jiwei-life。

关系说明：前端 jiwei-miniapp/src/pages/note/detail.vue，后端 NoteServiceImpl.detail()（jiwei-modules/src/main/java/com/jiwei/social/service/impl/NoteServiceImpl.java:219-241）、UserNoteController.getDetail（.../social/controller/user/UserNoteController.java:138-142）。接口与页面都已实现，问题在数据与鉴权，分三处：

1. 【鉴权导致空壳】：WebMvcConfiguration（jiwei-framework/src/main/java/com/jiwei/framework/config/WebMvcConfiguration.java:41-45）拦截所有 /user/**，JwtTokenUserInterceptor 无 token 返回 401；而前端 getNoteDetail 在 api/index.js:250-253 用了 silent:true，401 被静默 reject 后页面只渲染空壳。请排查：小程序浏览类接口是否不该强制鉴权（可考虑为 note 详情这类只读接口放开鉴权，或前端确认 token 已注入 header）。给出最小改动方案，不要影响需登录的写操作。

2. 【images 为 NULL】：classpath 下数据库插入数据（db/99_seed_data.sql 的笔记 INSERT、db/generate_mock_data.mjs 的 mock 笔记生成）都未写 images 列。请：a) 在 mock 生成器 generate_mock_data.mjs 里给笔记补上 images（JSON 字符串，可复用现有图片 URL 池）；b) 后端 detail() 里对 images 为 NULL 时给出兜底（如空数组）或忽略，避免前端 v-if 因 null 报错。同时检查 NoteServiceImpl.detail() 是否对 images 加了 JSON 解析（把字符串解析成数组再返回）。

3. 【评论树缺失】：detail() 返回的评论是扁平 list，前端 detail.vue 按 c.children 渲染二级回复。请在后端 detail() 里把扁平评论构造成 parent -> children 结构，或在前端对扁平评论按 parentId 分组。二选一并说明选择理由。

改动保持最小，聚焦这三处，不动无关功能。
```

---

## Bug 5｜店铺没有详情界面

**现象**：店铺详情页只剩顶部横幅（「品质美食」/时间），下方菜品分类与菜品列表全部空白，像是没做。

**根因**：页面与后端接口**都已完整实现，数据也齐全**，不是没做。**最可能是鉴权静默失败**：`jiwei-miniapp/src/pages/shop/shop.vue:324-343` 的 `onMounted` 用 `Promise.all` 同时请求 `getMerchantDetail(id)` + `getMerchantDishes(id)`（`api/index.js:64-87`，均 `silent:true`），任一 401 进 catch（shop.vue:339-341）导致 `shop/categories/dishes/setmeals` 全保持空，只剩默认横幅文案。后端 `UserShopController`（`.../merchant/controller/user/UserShopController.java:56-74`、97-103）接口齐全，`MerchantInfoServiceImpl`/`DishServiceImpl`/`SetmealServiceImpl` 及 Mapper 均已 `@DS("merchant")` 正确路由，`db/99_seed_data.sql` 店铺 1-4 有分类/菜品/套餐数据。

**修复提示词（直接复制给 AI）**：

```text
你是 uni-app 微信小程序 + Spring Boot 后端工程师，请修复「店铺详情页空白（只剩顶部横幅）」问题。项目根目录 jiwei-life。

现状：jiwei-miniapp/src/pages/shop/shop.vue 的 onMounted（约 324-343 行）用 Promise.all 调 getMerchantDetail(id) + getMerchantDishes(id)，两者在 api/index.js:64-87 都用了 silent:true。后端 UserShopController 的 /user/merchant/{id} 与 /user/merchant/{id}/dishes（jiwei-modules/src/main/java/com/jiwei/merchant/controller/user/UserShopController.java:56-74、97-103）均已实现且返回完整分类/菜品/套餐，数据库也有数据。

请排查并修复（不改动店铺数据/菜单逻辑本身）：
1. 先确认两个请求是否因未登录/令牌失效而被后端 JwtTokenUserInterceptor（jiwei-framework/src/main/java/com/jiwei/framework/interceptor/JwtTokenUserInterceptor.java:38-42，对 /user/** 强制鉴权，无 token 返回 401）拦截，前端 silent:true 导致 Promise.all 进 catch。给出最小方案：为这组只读接口处理鉴权（放开或确保 token 注入），或前端在 catch 里单独打印错误以便定位。
2. 请手动验证：若能拿到 token，直接请求 /user/merchant/{id}/dishes 应返回 {categories, dishes, setmeals} 且非空。若返回空，再排查 @DS("merchant") 数据源路由与 categoryService/dishService 的查询条件（type=1 分类、菜品 status=1）。但在确认鉴权问题前不要改查询逻辑。

改动最小，聚焦 shop.vue 和鉴权/接口调用，不重构店铺菜单展示。
```

---

## Bug 6｜AI 助手对话先后顺序显示不对

**现象**：AI 助手聊天里，用户消息和 AI 回复顺序混乱——AI 消息整体排到用户消息上面，且每次回复有一个「正在规划中...」僵尸气泡。

**根因（前端数组管理乱序）**：`jiwei-miniapp/src/pages/aichat/aichat.vue` 用**两个独立数组**：`messages`（51 行，只存 AI/bot 消息）与 `userMessages`（52 行，只存用户消息）。模板第 5-28 行用**两段堆叠的 v-for**：先渲染全部 `messages`（bot 气泡），再渲染全部 `userMessages`（用户气泡）。DOM 顺序是固定的「所有 AI → 所有用户」，所以时间顺序永远错乱，AI 回复插在用户消息前面。问题叠加：`send()`（62-98 行）第 68 行 push 一条占位「🤖 正在规划中...」后**从不清除**，第 91 行又 push 一条最终回答，每次发送多出一条僵尸气泡；每条消息**无唯一 id**，`:key` 用数组下标（第 6、24 行）。

前端**不是流式**（`api/index.js:348-363` 调一次性 POST /ai/agent/chat）。后端 SSE `/ai/agent/stream` 与一次性 JSON `/ai/agent/chat`（`AgentController.java:185-189`、`AgentChatService.java:35-88`）顺序均正确，非本 bug 来源。

**修复提示词（直接复制给 AI）**：

```text
你是 uni-app 微信小程序工程师，请修复「AI 聊天消息顺序错乱 + 出现“正在规划中”僵尸气泡」问题。项目根目录 jiwei-life。

根因：jiwei-miniapp/src/pages/aichat/aichat.vue 用两个独立数组 messages（只存 AI 消息，约 51 行）与 userMessages（只存用户消息，约 52 行），模板第 5-28 行用两段堆叠的 v-for：先渲染全部 AI 气泡再渲染全部用户气泡，因此无论何时发送，用户消息永远落在整段 AI 消息下方，时间顺序错乱。另外 send()（约 62-98 行）第 68 行 push 了一条「🤖 正在规划中...」占位消息但从未删除，第 91 行又 push 最终回答，每次发送多出一条僵尸气泡；每条消息无唯一 id，:key 用数组下标（第 6、24 行），易错乱。

修复要求：
1. 把消息合并成**单一数组**（例如 chatMessages），每条消息对象带上 role 字段（'user' / 'assistant'）与唯一 id（可用时间戳+自增）。模板改用**单个 v-for**，按 item.role 决定气泡靠左（assistant）还是靠右（user），CSS 类对应 .msg-row.bot / .msg-row.user。
2. send() 流程：先 push 用户消息（role='user'），再 push 一条 assistant 占位（text 可为「正在思考中...」），并把该占位消息的 id 记下来；请求返回后**原地更新**这条占位消息的 text（用 id 找到它赋值，而不是再 push 一条新的）。
3. 删除旧的 messages / userMessages 双数组与两段 v-for，只保留一份渲染。
4. 欢迎语作为第一条 assistant 消息加入 chatMessages。
5. 历史消息若后端返回（当前前端没加载历史，可不做），请按时间正序 push，勿倒序。

改动只限 aichat.vue，不动后端 AI 服务（其后端一次性与 SSE 顺序均正确）。
```

---

## Bug 7｜推荐 / 热门只显示几条笔记

**现象**：后台笔记数据充足（mock 220 篇），但小程序「推荐」「热门」两个 tab 都只显示很少几条。

**根因**：

- 前端无分页：`jiwei-miniapp/src/pages/note/note.vue:69-76` 固定 `page=1, pageSize=20`，无 `onReachBottom`/加载更多，翻页参数从不变化。
- 「推荐」实际是**关注流**：后端 `NoteServiceImpl.feed()`（`.../social/service/impl/NoteServiceImpl.java:100-137`）先读 `feed:{userId}` 收件箱，但 `publish()` 从未写粉丝收件箱（94-96 行只有 TODO），该 ZSet 恒空；降级查 DB 时用 `user_id IN (followeeIds)`，只返回被关注作者的笔记。mock 笔记作者是 user 100-400，种子关注关系只在 user 1-5 之间，导致只看到极少数。
- 「热门」依赖未预热的 `hot_notes:weekly` ZSet：`hotRanking()`（140-166 行）只在该 ZSet 存在时用它，而该 ZSet 只在浏览/点赞/收藏（`updateHotScore` 352-357 行）时被写入，**启动时从不预热**（`LikeCountWarmUpRunner.java:26-38` 只预热 `note:like:count`）。一旦有交互，ZSet 里只有被交互过的几条 → 热门只显示这几条。

**修复提示词（直接复制给 AI）**：

```text
你是 Spring Boot 后端 + uni-app 前端工程师，请修复「笔记推荐/热门只显示几条」问题。项目根目录 jiwei-life。

关系说明：前端列表面板 jiwei-miniapp/src/pages/note/note.vue（推荐/热门 tab 切换约 69-76 行）；后端 NoteServiceImpl 的 feed()（推荐，jiwei-modules/src/main/java/com/jiwei/social/service/impl/NoteServiceImpl.java:100-137）与 hotRanking()（热门，140-166 行）。

修复：
1. 【推荐 feed()】当前是「过滤只看关注用户的笔记」：先读 feed:{userId} 收件箱（恒空，因 publish() 约 94-96 行从未写粉丝收件箱，只有 TODO），再降级查 user_id IN (followeeIds)。请改成：未关注任何人时返回「全站笔记流」（audit_status=1，按时间倒序，含分页），只有真正有关注时才用关注流；或将 feed 收件箱在 publish() 时写入粉丝（需粉丝列表逻辑）。二选一，优先实现「全站流降级」，保证新用户能刷到足够多笔记。保留分页参数。
2. 【热门 hotRanking()】当前依赖 hot_notes:weekly ZSet，但启动时不预热（LikeCountWarmUpRunner 只预热 note:like:count，jiwei-life 后端 .../social/runner/LikeCountWarmUpRunner.java:26-38）。请在应用启动时用数据库里的笔记（浏览/点赞/收藏等指标）初始化该 ZSet，或在 ZSet 未预热/数据过少时降级为「按热度字段全站排序」。确保热门能展示一批而不是只有几条。
3. 【前端分页】note.vue 加 onReachBottom 加载更多，page++ 调用同一接口，追加到列表，替换固定 page=1。
4. 不改动笔记实体结构与图片字段，不改数据库表结构。

改动聚焦 feed()/hotRanking()/LetWarmUpRunner 与 note.vue。请特别说明推荐 feed 你选了哪种方案及原因。
```

---

## Bug 8｜笔记标签要能跳转对应店铺 / 搜索

**现象**：笔记里的标签（#面食地图 #深夜食堂）是纯文本，不能点击跳转；用户希望标签能跳转到对应店铺或搜索栏（设计文档里有「短链」思路）。

**根因（完全没实现）**：

- 前端列表页 `note.vue:28-30` 标签用 `<text class="tag">#{{ t }}</text>` 渲染，**无 @click**，不可点击。
- 详情页 `detail.vue` **根本没有渲染 topicTags**，只有基于 `merchantId` 的「关联店铺」入口（14-17 行）可跳店铺。
- 数据侧：`Note` 实体只有 `merchantId`（关联店铺）和 `topicTags`（话题标签 JSON 字符串数组，`jiwei-pojo/.../entity/Note.java:30-33`）。**topicTags 只是字符串数组，没有任何「标签→店铺 id」映射字段**。因此标签目前唯一能做的跳转是「拿标签文本去搜索」。
- 文档 `docs/superpowers/plans/2026-08-26-market-seckill-shortlink.md` 里的「短链」是 market 模块的通用 URL 短链，与笔记标签跳转无关，不能照搬。

**修复提示词（直接复制给 AI）**：

```text
你是 uni-app 微信小程序前端工程师，请实现「笔记标签可点击跳转」功能。项目根目录 jiwei-life。

现状：笔记的 topic_tags 只是字符串数组（如 ["面食地图","深夜食堂"]），Note 实体（jiwei-pojo/.../entity/Note.java:30-33）只有 merchantId（关联店铺 id）作为「店铺」锚点，没有「标签→店铺 id」映射。

分两步实现，先做可落地的最小可行版：

1. 【点击标签跳搜索】（必做）：在列表页 jiwei-miniapp/src/pages/note/note.vue 的标签 <text class="tag">#{{ t }}</text>（约 28-30 行）加 @click，读取当前标签文本，navigateTo 到搜索页（jiwei-miniapp/src/pages/search），并把该标签词带入搜索框/作为关键词发起搜索（搜索页需支持从 query 接收 keyword 并自动执行）。同时确认详情页 detail.vue 也要渲染 topicTags（当前完全没渲染），复用同样的点击跳搜索逻辑。
2. 【标签跳对应店铺】（可选增强，需后端配合）：若数据里有「标签→店铺」映射（如每个 topicTag 关联一个 merchantId），则点击标签时可跳转对应店铺；若没有，则明确保持只跳搜索，并说明为何当前做不到「直接跳店铺」（缺映射字段）。不要为做映射而改库表结构，除非设计文档明确要求。

要求：改动最小，只动 note.vue、detail.vue、与 search 页的 keyword 接收。给出你对「标签能否直接定位店铺」的结论与依据。
```

---

## 共性根因（多个 bug 的同一病灶）

**鉴权门 + silent:true 静默吞 401**：后端 `WebMvcConfiguration`（`jiwei-framework/src/main/java/com/jiwei/framework/config/WebMvcConfiguration.java:41-45`）拦截所有 `/user/**`，`JwtTokenUserInterceptor`（同拦截器目录，无 token 返回 401）；而小程序 `api/index.js` 的封装里大量浏览类接口（笔记详情、店铺详情、店铺菜单、地址等）设了 `silent:true`，401 被静默 reject，页面就只渲染空壳。Bug 4、Bug 5 是直接受害者，Bug 2 的「保存失败」也可能与此相关。

建议统一排查：对**只读浏览类接口**放开鉴权（或提供游客态），**写操作类接口**保持鉴权；同时在前端请求拦截器里确认真实登录后 token 是否正确注入 header，避免误判为静默 401。
