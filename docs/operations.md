# Operations

## Compatibility Note

`kk-common:0.1.2` 的 `requestContextFilter` bean 名称与 Spring Boot 3.5.x 自带 bean 名称冲突，因此当前应用显式开启 `spring.main.allow-bean-definition-overriding=true`，由公共包过滤器接管 RequestContext。升级公共包后应重新验证并移除此兼容配置。

## Configuration

生产环境必须设置：

- `KMESSAGE_ADMIN_USERNAME`, `KMESSAGE_ADMIN_PASSWORD`: 管理后台 Session 登录凭据。
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`: PostgreSQL 连接。
- 渠道凭据通过管理后台直接配置：
  - **Telegram**：填入 Bot Token（形如 `123456789:ABC-DEF...`，由 @BotFather 的 `/newbot` 创建）。目标 `target` 可填数字 chat id（群聊为负数）或公开频道用户名 `@channelusername`；首次发送前目标需先主动联系 Bot 以取得 chat id。
  - **飞书**：填入 `app_id:app_secret`（在飞书开放平台「凭证与基础信息」获取）。需开通权限：`im:message:send_as_bot`（发送消息）、`im:chat:readonly`（列出群组）、`contact:user.id:readonly`（通过手机号/邮箱获取用户 ID）、`contact:user.employee_id:readonly`（获取用户 user ID）、`contact:user.phone:readonly`（手机号查询）、`contact:user.email:readonly`（邮箱查询），完成应用发布与「通讯录权限范围」配置。`target` 支持 `open_id`（`ou_` 开头）、`user_id`、`union_id`（`on_` 开头）、`chat_id`（`oc_` 开头，机器人需先入群）或 `email`，适配器按 target 形态自动选择 `receive_id_type`；如需固定类型可在渠道实例 `configJson` 中设置 `{"receive_id_type":"user_id"}`。手机号批量导入：中国大陆号可不带区号（自动补 +86），国际号需带 + 国家码。

渠道凭据存入数据库，API 和日志中脱敏展示。

`KMESSAGE_RUNTIME_ROLE` 支持 `all`、`api` 和 `worker`。横向扩展 worker 时，数据库任务租约保证同一时刻最多一个 worker 处理任务。异常退出后，租约到期的任务可被重新领取。

## Retry Policy

网络错误、HTTP 408、429 和 5xx 使用带抖动的指数退避。其他 HTTP 错误视为永久失败。默认最多尝试 5 次，可使用 `KMESSAGE_WORKER_MAX_ATTEMPTS` 调整。

## Monitoring

Actuator 暴露 `health`、`metrics` 和 `prometheus`：

- `kmessage.delivery.backlog`: 当前可领取任务数。
- `kmessage.delivery.attempts`: 按渠道和结果分类的投递次数。

对积压持续增长、`PERMANENT_FAILURE` 增长和健康检查失败配置告警。

## Credential Rotation

通过管理后台直接更新渠道实例的凭据。飞书应用凭据轮换：在飞书开放平台重新生成 App Secret 后，在管理后台更新对应渠道的凭据值，适配器会在下次投递时自动获取新的 tenant_access_token。应用 secret 轮换会保留 appKey、立即停用旧 appSecret，并只展示一次新 appSecret。

管理后台 Session cookie 为 HttpOnly。生产部署必须使用 HTTPS，并在反向代理或 Spring Session 配置中启用 Secure cookie 和合适的 Session 超时时间。

## Initial Capacity Guidance

首期数据库任务队列面向低到中等通知流量。建议从每个 worker 单批 20 条、1 秒轮询开始；压测时重点观察数据库锁等待、积压、渠道限流和 P95 投递延迟。达到数据库瓶颈后再迁移专用消息队列。
