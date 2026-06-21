# kMessage

面向内部服务的统一消息平台。首期支持 Telegram 文本消息、飞书文本/卡片消息；邮箱仅保留扩展类型，不能启用或发送。

## 技术栈

- Spring Boot 3.5.6+ / JDK 17+
- 单模块 Maven
- PostgreSQL（生产）/ H2（本地与测试）
- Spring Data JPA、Flyway、Spring RestClient、JUnit 5
- Vue 3、Vite、`@kk-2004/ui-components`（公共控件直接复用），element-plus（补充 select/table 等）
- `com.kK-2004:kk-common:0.1.2`：统一响应、异常处理和 RequestContext
- 环境变量 secret provider：渠道凭据使用 `env:VARIABLE_NAME` 引用

Redis 与 Redisson 默认关闭。可靠任务队列使用关系型数据库事务和行锁。

## 本地运行

```bash
export KMESSAGE_ADMIN_USERNAME=admin
export KMESSAGE_ADMIN_PASSWORD='replace-with-a-strong-password'
mvn spring-boot:run
```

API 与 worker 默认在同一进程运行。可分离部署：

```bash
KMESSAGE_RUNTIME_ROLE=api java -jar target/kmessage-0.1.0-SNAPSHOT.jar
KMESSAGE_RUNTIME_ROLE=worker SPRING_MAIN_WEB_APPLICATION_TYPE=none java -jar target/kmessage-0.1.0-SNAPSHOT.jar
```

本地排查接口异常时可启用 `dev` profile，响应会包含具体异常类型，服务端日志会记录完整堆栈：

```bash
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

打开 `http://localhost:8002/`，系统会跳转至管理后台登录页。管理操作使用浏览器 Session，不存在 `admin-key`。

业务应用在管理后台创建后获得 `appKey` 和只展示一次的 `appSecret`。HTTP 请求使用 `X-App-Key` 和 `X-App-Secret`。

## 前端开发

管理后台源码位于 `frontend/`，公共控件直接复用 `@kk-2004/ui-components`，下拉、表格等共享库未提供的控件使用 element-plus。开发时先运行后端，再启动 Vite：

```bash
cd frontend
pnpm install
pnpm dev
```

Vite 默认把 `/api` 代理到 `http://localhost:8002`。`pnpm build` 会将生产资源写入 `src/main/resources/static/admin`；Maven 构建也会自动安装前端依赖并执行该构建。

## Java SDK

独立 SDK 位于 `kmessage-sdk/`，不会引入服务端 Spring Boot 依赖：

```bash
mvn -f kmessage-sdk/pom.xml install
```

```java
KMessageClient client = new KMessageClient(endpoint, appKey, appSecret);
var result = client.send(new KMessageClient.SendMessage(
        channelInstanceId, target, "hello", idempotencyKey, Map.of()));

var textResult = client.send(
        channelInstanceId,
        target,
        new KMessageClient.NormalMessage("普通通知"),
        "idem-text-1");

var cardResult = client.sendToUser(
        channelInstanceId,
        userId,
        new KMessageClient.CardMessage(Map.of(
                "config", Map.of("wide_screen_mode", true),
                "header", Map.of(
                        "template", "blue",
                        "title", Map.of("tag", "plain_text", "content", "通知标题")),
                "elements", List.of(Map.of(
                        "tag", "div",
                        "text", Map.of("tag", "lark_md", "content", "**状态**：成功"))))),
        "idem-card-1");
```

## 构建

私有 GitHub Packages 依赖需要在 Maven `settings.xml` 为仓库 id `github` 配置具有 `read:packages` 权限的凭据。

```bash
mvn clean verify
```

详细部署和运维说明见 [docs/operations.md](docs/operations.md)。
