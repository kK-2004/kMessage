## Why

当前 IM 渠道都不可用：飞书适配器把官方返回的 `expire` 绝对时间戳当成相对秒数叠加，导致 tenant_access_token 缓存几乎立即过期并频繁刷新；飞书 `receive_id_type` 被硬编码为 `open_id`，无法支持群聊或 `user_id`/`email` 等合法接收方；Telegram 目标校验只允许纯数字 `chat_id`，无法使用 `@channelname` 这类官方支持的频道用户名。同时钉钉渠道不再投入运营，现有控制台却仍把所有渠道一视同仁地暴露，且整体页面观感陈旧、表单与列表仍混用原生 `<select>` 和裸表格，缺少一致的视觉语言。

## What Changes

- 按飞书开放平台官方文档修正飞书适配器：`tenant_access_token/internal` 返回的 `expire` 是绝对到期时间戳（秒），改为 `Instant.ofEpochSecond(expire)` 缓存；保留 `FEISHU_99991401` token 失效后单次刷新逻辑。
- 飞书消息投递支持按 target 形态选择 `receive_id_type`（`open_id` / `user_id` / `union_id` / `email` / `chat_id`），由适配器识别 target 前缀或渠道实例配置自动选择，默认 `open_id`。
- 按 Telegram Bot API 官方文档修正目标校验：接受正负数字 `chat_id` 与 `@channelusername` 两种格式，并校验 `text` 不超过 4096 字符。
- 更新 `ChannelType` 元数据（`credentialHint`、`setupGuide`、`targetHint`）以匹配官方文档。
- **BREAKING**：完全移除钉钉渠道——删除 `DINGTALK` 枚举值、`HttpChannelAdapters.DingTalk` 适配器及其相关代码，钉钉不再作为支持渠道出现在管理后台或渠道类型列表中。
- 把渠道行的「测试」按钮升级为「发消息」按钮：点击后从渠道方拉取可选发送目标（Telegram 调 `getUpdates` 取最近联系过 Bot 的聊天，飞书调 `im/v1/chats` 取机器人所在群组），用户从列表选择或手动输入 target 后发送；保留手动输入降级入口。
- 拉取到的联系人透明增量持久化到 `channel_contacts` 表：用户/群组 id 不变，每次拉取按 (渠道实例, targetId) upsert（更新 label、刷新 lastSeenAt），「发消息」对话框读取的是持久化的全量历史联系人，从而克服 Telegram `getUpdates` 仅暴露 48 小时窗口的限制。
- 支持渠道删除：删除前预览该渠道被哪些应用授权、关联多少历史消息，用户确认后强制级联删除（attempts → tasks → messages → grants → contacts → 渠道实例）。
- 支持渠道编辑：可修改名称、启用状态、凭据引用（如 Telegram Bot Token）、高级配置 configJson；凭据因服务端脱敏，编辑时留空表示不修改。
- 新增「应用 + 渠道」作用域的用户与分组管理：通过手机号/邮箱批量导入（飞书调 `batch_get_id` 解析 open_id），用户以树形分组组织、可属于多个组；SDK 支持对某个组（fan-out 到所有成员）或某个用户发消息。
- 使用 `@kk-2004/ui-components` 重构登录页与控制台布局、卡片、表单、对话框与状态展示；对共享库未覆盖的表格、下拉、消息提示等控件，引入 `element-plus` 作为补充。
- 重写整体视觉：顶部导航 + 单列工作区 + 分栏面板栅格，统一间距、圆角、配色与空态/加载态，替换裸 `<select>` 与裸表格。

## Capabilities

### New Capabilities

- `user-groups`：按「应用 + 渠道」作用域的用户分组树——手机号/邮箱批量导入、用户多组、树形分组（增删改、防环路）、SDK 对组/用户发送（组发送在服务端 fan-out）。

### Modified Capabilities

- `channel-configuration`: 渠道类型元数据按官方文档重写（Telegram chat_id/@channel、飞书凭据格式与 receive_id_type）；钉钉渠道类型及其适配器完全移除；新增渠道删除能力（预览 + 强制级联删除）；新增渠道编辑能力（名称/启用/凭据/configJson）；新增联系人持久化（增量 upsert 到 `channel_contacts`）。
- `message-delivery`: 修正飞书 tenant_access_token 过期时间缓存语义、飞书 receive_id_type 选择、Telegram 目标校验，使 Telegram 与飞书适配器按官方文档实际可用；移除钉钉适配器；适配器新增联系人枚举能力供管理后台「发消息」使用。
- `vue-admin-console`: 基于 `@kk-2004/ui-components` 与 `element-plus` 重构控制台视觉与布局；渠道行「测试」升级为「发消息」（联系人选择 + 手动降级），新增「编辑」「删除」操作。

## Impact

- 后端：`HttpChannelAdapters`（移除 DingTalk 类、修正 Feishu token 缓存与 receive_id_type、Telegram validate、新增 Telegram/Feishu `listContacts` 与 Feishu `lookupUsers`）、`ChannelType`（移除 DINGTALK、更新元数据）、`ChannelAdapter`（新增 `listContacts`/`lookupUsers` 默认方法）、`AdminController`（contacts/send-message/delete-preview/delete 端点 + 用户/分组管理端点）、`ChannelService`（previewDelete + 级联 delete）、新增 `UserGroupService`（用户导入、分组树 CRUD、成员、expandGroup）、`MessageService`（submit 展开 groupId/userId fan-out）、`GrantRepository`/`MessageRepository`/`TaskRepository`/`AttemptRepository`/`AppUserRepository`/`UserGroupRepository`/`UserGroupMemberRepository`（新增级联删除查询）、新增 `ContactOption`/`ResolvedUser` DTO、新增 V4(channel_contacts)/V5(app_users, user_groups, user_group_members) 迁移。
- SDK：`KMessageClient` 新增 `sendToGroup`/`sendToUser` + `MessageBatchResult`/`SendTargetMessage`。
- 前端：重构 `frontend/src/pages` 与 `frontend/src/components/admin`，`ChannelPanel` 增加发消息对话框与删除/编辑，新增 `UserGroupPanel`（用户导入 + 分组树 + 成员管理），新增 element-plus 依赖。
- 数据：`channel_type` 为 `varchar(32)` 列，移除枚举值无需迁移；既有钉钉实例（如有）将无法再投递，需在部署前清理。
- 依赖：`frontend/package.json` 新增 `element-plus`；后端无新增依赖。
- 文档：更新部署文档中飞书/Telegram 配置示例，移除钉钉相关说明。
