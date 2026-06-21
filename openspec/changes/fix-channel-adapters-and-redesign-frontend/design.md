## Context

kMessage 的 IM 适配器目前都无法成功投递。`HttpChannelAdapters` 中的实现存在与官方文档不符的问题，并且管理后台视觉陈旧。同时钉钉渠道决定不再投入运营。本变更以官方文档为准修正 Telegram 与飞书适配器，完全移除钉钉渠道，并基于共享组件库重构前端视觉。

当前实现的关键事实（来自代码核对）：

- 飞书适配器从 `tenant_access_token/internal` 解析响应后执行 `Instant.now().plusSeconds(expire)`，但飞书官方文档明确 `expire` 是**绝对到期时间戳（秒）**，应作为 `Instant.ofEpochSecond(expire)` 使用。当前写法几乎让缓存立即失效，触发频繁刷新。
- 飞书发送 URL 写死 `receive_id_type=open_id`，target 必须是 `ou_` 开头的 open_id；官方 `im/v1/messages` 支持 `open_id` / `user_id` / `union_id` / `email` / `chat_id` 五种。
- Telegram 目标校验为 `-?\d+`，但官方 `sendMessage` 的 `chat_id` 还可以是 `@channelusername` 形式的频道用户名。
- `ChannelType` 枚举同时承担展示元数据（label、credentialHint、setupGuide、targetHint）和实现可用性（`implemented()` 只排除 EMAIL）。
- 前端 `ChannelPanel.vue` 在创建表单里混用裸 `<select>`、`KInput`、`KButton`，列表用裸 div 表格；`AdminConsolePage.vue` 是简单纵向堆叠，无统一栅格与视觉层次。
- 钉钉适配器 `HttpChannelAdapters.DingTalk` 与 `ChannelType.DINGTALK` 存在，但已决定不再支持钉钉。

## Goals / Non-Goals

**Goals:**

- 让飞书和 Telegram 适配器按各自官方文档真实可用。
- 在不动调用方 HTTP 契约的前提下，扩展飞书对多种 receive_id_type 的支持。
- 完全移除钉钉渠道（枚举、适配器、元数据、前端选项、文档）。
- 用 `@kk-2004/ui-components` 配合 `element-plus` 重构控制台视觉与布局。

**Non-Goals:**

- 不改动统一消息信封、投递状态机、幂等与重试框架（`DeliveryService`、`MessageService` 核心逻辑保持不变）。
- 不增加新的渠道类型（邮箱仍保留为预留类型）。
- 不改动业务调用方的 appKey/appSecret 鉴权契约。
- 不引入前端路由库；登录态与控制台态仍由单 Vue 应用按 Session 状态切换。
- 不为既有钉钉实例提供数据迁移工具（部署前手工清理）。

## Decisions

### 飞书 token 缓存改用绝对时间戳语义

把 `fetchToken` 的 `new CachedToken(token, Instant.now().plusSeconds(expire))` 改为 `Instant.ofEpochSecond(expire)`，符合飞书 `tenant_access_token/internal` 官方文档中 `expire` 为绝对到期时间戳（秒）的定义。`TOKEN_REFRESH_BUFFER = 300` 秒的提前刷新缓冲保留不变。这是最小的正确性修复，无需改动缓存结构 `CachedToken(token, expiresAt)`。

### 飞书 receive_id_type 由 target 形态推导，可被渠道实例配置覆盖

`im/v1/messages` 的 `receive_id_type` 从 query 参数移到按 target 自动选择，规则（对齐飞书 open_id/user_id/union_id/email/chat_id 前缀约定）：

- `ou_` 前缀 → `open_id`
- `on_` 前缀 → `union_id`
- `@` 结尾且为邮箱形 → `email`
- 纯数字或 `oc_` 前缀 → `chat_id`
- 其他 → `user_id`

为保留确定性，渠道实例的 `configJson` 可选字段 `receive_id_type` 一旦存在则覆盖自动推导（强制使用某一种 ID 类型），默认不设置走自动推导。这样不破坏现有 open_id 调用方，同时支持群聊与其他 ID 类型。

### Telegram 目标校验放宽到数字与 @username

把 `if (!target.matches("-?\\d+"))` 改为接受 `^-?\d+$` 或 `^@[A-Za-z0-9_]{5,32}$`（Telegram 公开频道/群用户名规则）。同时在校验阶段检查 `text` 长度不超过 4096 UTF-16 code units（Telegram 官方上限），超长直接归类为永久失败，避免无效调用。

### 完全移除钉钉渠道

钉钉不再支持，因此彻底删除而非置灰：

- 删除 `ChannelType.DINGTALK` 枚举值及其元数据。
- 删除 `HttpChannelAdapters.DingTalk` 适配器类（`@Component`），渠道适配器注册表自然不再包含钉钉。
- `channel_type` 数据库列为 `varchar(32)`，移除枚举值无需 schema 迁移；既有钉钉实例无法再投递，需在部署前手工清理。
- 不引入 `availableForCreation` 维度——移除钉钉后，「不可创建」等价于「未实现」（仅 EMAIL），因此沿用既有 `implemented()` 作为创建与启用校验，避免增加与 `implemented()` 语义重复的字段。

### 「发消息」按钮 + 联系人枚举

把渠道行原有的「测试」按钮升级为「发消息」。点击后对话框先从渠道方拉取可选目标列表，用户从列表选择或手动输入后发送。两端的拉取能力由适配器按各自官方 API 实现：

- `ChannelAdapter` 新增可选方法 `List<ContactOption> listContacts(ChannelInstance)`，默认返回空列表（不破坏契约）。`ContactOption(id, label, type)` 为新增 DTO，`type` 取值 `user`/`group`/`channel`。
- **Telegram**：调 `GET /bot<token>/getUpdates`（`RestClient.get()`，不传 offset，仅读不消费），解析 `result[].message.chat`（兼容 `my_chat_member.chat`），按 `chat.id` 去重，`chat.type` 为 group/supergroup 归类 `group`、channel 归类 `channel`、其余归类 `user`；label 优先用群标题，私聊用 `first_name+last_name`，再退化到 `@username` 或 id。这符合 Telegram「Bot 只能取得先联系过它的聊天」的固有语义，正好回答了「chat id 怎么来」的问题。
- **飞书**：用已有 tenant_access_token 调 `GET im/v1/chats?user_id_type=open_id&page_size=50` 取机器人所在群组，归类 `group`。拉取单个用户需额外通讯录权限（`contact:user.id:readonly` 等，需管理员审批），首期不纳入；私聊目标走手动输入 open_id。
- **降级**：拉取失败或列表为空时，对话框自动切到手动输入，并显示该渠道的 `targetHint` 格式提示。

`AdminController` 新增 `GET /channels/{id}/contacts` 返回 `List<ContactOption>`；原 `POST /channels/{id}/test` 重命名为 `/channels/{id}/send-message`（请求体同为 `{target, text}`，直接调适配器 `send` 同步返回 `DeliveryResult`，不持久化、不走 worker）。

### 渠道删除（预览 + 强制级联）

渠道被 grants/messages/delivery/contacts 引用，FK 无 `ON DELETE CASCADE`，故删除分两步：

- **预览** `GET /channels/{id}/delete-preview` 返回 `DeletePreview(grantedAppCount, appNames, messageCount)`，由 `ChannelService.previewDelete` 查询 `grants.findByChannelInstanceId` 解析应用名 + `messages.countByChannelInstanceId`。前端据此弹确认框。
- **强制删除** `DELETE /channels/{id}` 在一个 `@Transactional` 中按 FK 依赖顺序删除：先 `attempts.deleteByMessageIdIn(messageIds)`、`tasks.deleteByMessageIdIn(messageIds)`、`messages.deleteByChannelInstanceId`，再 `grants.deleteByChannelInstanceId`、`contacts.deleteByChannelInstanceId`，最后 `channels.deleteById`。`messageIds` 通过原生 SQL `select id from messages where channel_instance_id=?` 取得（JPQL 嵌套实体名 `Message` 在该持久化单元解析失败，故用 native query）。
- 这是硬删除，会丢失该渠道的历史投递记录与联系人历史——预览步骤确保用户知情后才继续。

### 联系人持久化（增量同步）

用户/群组 id 在各渠道内稳定不变，故每次拉取的联系人做增量持久化，克服 Telegram `getUpdates` 仅暴露 48 小时窗口的限制：

- 新表 `channel_contacts`（迁移 `V4__channel_contacts.sql`），复合唯一键 `(channel_instance_id, target_id)` 即增量去重键；字段 `label`、`contact_type`、`first_seen_at`、`last_seen_at`。FK 引用 `channel_instances(id)`，不加 `ON DELETE CASCADE`（由渠道删除级联显式删）。
- 实体 `Entities.ChannelContact extends Base`，Repository `ChannelContactRepository`。
- `ChannelService.syncContacts(channelInstanceId, fetched)`：对每个 `ContactOption` 按 (渠道实例, targetId) 查现有——存在则更新 label + 刷新 lastSeenAt，不存在则插入（firstSeenAt = lastSeenAt = now）；最后返回持久化的全量联系人（按 lastSeenAt 倒序）。
- **透明同步**：`GET /channels/{id}/contacts` 端点先调 adapter.listContacts 拿实时列表 → syncContacts 增量入库 → 返回持久化全量。前端无需感知增量逻辑，只读后端返回的合并列表。

### 渠道编辑（全字段，扩展 name）

现 `ChannelService.update` 只支持 enabled/credentialRef/configJson，扩展为含 name：

- `update(id, name, enabled, credentialRef, configJson)`：name 为 null 表示不改；非 null 时校验非空 + `channels.findByName` 重名校验（排除自身，复用 create 的 409 语义）。
- `UpdateChannel` record 增加 `name` 字段，`PUT /channels/{id}` 端点签名不变（请求体多一个 name，向后兼容）。
- **凭据脱敏对编辑 UX 的影响**：`ChannelService.view` 返回的 credential 是 `redacted:xxxxxxxx`，前端无法回填原值。约定：编辑对话框凭据字段初始为空，留空表示不改（payload 不含 credentialRef，后端 null = 不变）；填了新值才覆盖。UI 须明确提示「留空表示不修改」，避免用户误以为清空了凭据。

### 用户分组树（用户属渠道，分组属应用）

用户 id 在各渠道内稳定不变（飞书 open_id、Telegram chat id），但同一人在不同渠道的 id 不同。**用户属于渠道**（被所有绑定该渠道的应用共享），**分组属于应用**（每个应用在渠道下有自己的分组树，从渠道用户里挑成员）。

**数据模型**（迁移 `V5__user_groups.sql`）：
- `app_users(id, channel_instance_id, target_id, name, phone, email, created_at)`，唯一键 `(channel_instance_id, target_id)` —— **渠道级，无 caller_id**。
- `user_groups(id, caller_id, channel_instance_id, parent_id [自引用, null=根], name, created_at)`，邻接表树，**应用+渠道作用域**。
- `user_group_members(group_id, app_user_id)`，复合主键，用户多组多对多（成员引用渠道级 app_users）。

**手机号/邮箱批量导入**（`UserGroupService.importUsers(channelInstanceId, ...)`，渠道级）：调 `ChannelAdapter.lookupUsers`（飞书实现调 `POST contact/v3/users/batch_get_id?user_id_type=open_id`，body `{mobiles, emails}`），成功解析的 upsert 到 app_users（按 (channel, target_id) 去重），未解析的输入返回到 `unresolved` 列表。需飞书开通 `contact:user.id:readonly` + `contact:user.phone/email:readonly` 权限且配置通讯录可见范围，范围外的查不到进失败列表。Telegram 等无通讯录 API 的渠道 `lookupUsers` 默认返回空（不支持手机号导入）。

**渠道联系人自动合并**（`UserGroupService.listUsers(channelInstanceId)`）：列出渠道用户时，把 `channel_contacts`（getUpdates / im chats 拉取的）里尚不在 app_users 的联系人自动 upsert 进 app_users（渠道级）。这样 Telegram 等只能通过 getUpdates 获取用户的渠道，其联系人也能进入用户列表被分组。

**分组树 CRUD**（应用+渠道作用域）：`parent_id` 自引用，防环路（`createsCycle` 沿 parentId 链上溯检测）；删组时子组重挂到被删组的父（或根），成员关系解除但渠道用户保留。成员加入时只校验用户属于该渠道（用户是共享的）。

**群组/用户发送展开**（`MessageService.submit`）：三种定向 target/groupId/userId 互斥。groupId 时 `expandGroup(callerId, channel, groupId)` 先按 callerId 校验 group 归属，再展开成员（渠道级 app_users，无 callerId 过滤），为每个成员创建一条独立 Message + DeliveryTask，每条 idempotencyKey = 原始 key + ":" + memberId。userId 时 `resolveUser(channel, userId)` 渠道级解析（grant 校验已在 submit 前完成）。fan-out 在 submit（不在 deliver），保留现有 per-message 重试/状态机语义不变。返回 `BatchView(totalMessages, messages)`；单目标/单用户仍返回单个 `MessageView`（向后兼容）。

**渠道删除级联**：`ChannelService.delete` 顺序中加入先 null 化 user_groups.parent_id（避免自引用 FK 冲突）、删 user_group_members、删 user_groups、删 app_users。删渠道用户会同时解除所有应用下引用它的成员关系（成员表按 appUserId 引用）。

### 前端视觉系统：共享库优先 + element-plus 补齐

布局决策：

- **顶部导航栏**：复用现有 `AdminTopbar`，重做为固定高度、带品牌标识、刷新与登出按钮的横向条，使用 `KButton`。
- **单列工作区**：内容容器最大宽度受限、垂直堆叠，移除当前裸堆叠带来的拥挤感。
- **分栏面板栅格**：`ApplicationPanel` 与 `ChannelPanel` 用 CSS Grid 两列在大屏、单列在小屏。
- **表单**：原生 `<select>` 全部替换为 `element-plus` 的 `ElSelect` / `ElOption`（不可用渠道按 `implemented === false` 禁用）；文本输入仍用 `KInput`。
- **列表**：裸 div 表格替换为 `element-plus` 的 `ElTable`，保留 `KStatusBadge`、`KLongText`、`KCopyButton` 等共享组件作为单元格内容。
- **对话框与测试发送**：保留 `KDialog` 系列（共享库已提供），内部表单按上述规则统一。
- **消息提示**：`useTDesignToast` 已在 `useAdminConsole.js` 中通过共享库使用，保持不变；仅在需要 `ElMessage` 的内联反馈场景补充使用 element-plus。

依赖：在 `frontend/package.json` 的 `dependencies` 新增 `element-plus`。共享库优先原则体现在：凡共享库已导出的控件一律用共享库，element-plus 只补缺口。

## Risks / Trade-offs

- [飞书 target 自动推导可能误判 user_id] → 自动推导规则覆盖常见前缀，对无法识别的形态默认 `user_id`（飞书内部用户 ID 无固定前缀），并提供 `configJson.receive_id_type` 作为强制覆盖逃生口。
- [Telegram @username 仅适用公开频道/群] → 校验只做格式合法性，实际可达性由 Telegram 在投递时返回；校验失败归类为永久失败，符合现有错误分类约定。
- [移除钉钉导致既有钉钉实例失效] → 列类型为字符串无需迁移；部署前需手工清理 `channel_instances` 中 `channel_type='DINGTALK'` 的行，并通知调用方停止使用这些实例。
- [引入 element-plus 增加前端包体积] → element-plus 支持按需引入；本变更只在 select/table/message 三类控件使用，配合 Vite 的 tree-shaking 控制体积。
- [前端重构可能引入回归] → 以现有管理 API 契约为锚点，逐组件替换并保留 `useAdminConsole.js` 的状态与动作签名不变；`AdminConsolePage.vue` 的 props/emits 接口保持稳定。

## Migration Plan

1. 先合并后端适配器修正（飞书 token、receive_id_type、Telegram 校验）与钉钉移除，单独验证单元/契约测试通过。
2. 重构前端视觉与组件，本地用 Vite dev server 对接后端逐页验证登录、应用、渠道、授权、测试发送流程。
3. 部署前清理数据库中既有 `DINGTALK` 渠道实例与相关授权。
4. 通过 Maven 打包验证前端产物进入 jar，并回归 `/admin/` 页面与 `/api/admin/**` 行为。

回滚：后端修正与钉钉移除可按文件单独 revert；前端重构若出问题，可回退到上一版 `frontend/` 源码并重新打包，后端 API 契约未变。

## Open Questions

- 飞书 `configJson.receive_id_type` 强制覆盖是否需要在管理后台暴露为可见表单字段？当前决策是保留为隐藏高级配置，必要时通过 API 直接写入。
