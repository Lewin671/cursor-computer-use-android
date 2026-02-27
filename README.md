# Pomodoro Sync（番茄钟多端同步）

一个完整的前后端分离工程：

- **Android 客户端（Kotlin + Compose + MVVM + Room + Retrofit）**
- **Go 后端（Gin + SQLite + JWT + Refresh Token）**
- 支持登录鉴权、番茄钟状态同步、历史同步、并发冲突处理、重启恢复。

---

## 1. 技术栈

### Android

- Kotlin
- Jetpack Compose
- MVVM + Repository
- Room（本地持久化）
- Retrofit + OkHttp（网络层）
- Coroutines + Flow（异步与状态）
- Foreground Service（后台计时 + 通知）
- WorkManager（周期同步）
- EncryptedSharedPreferences（安全存储 token）

### Backend

- Go + Gin
- SQLite（持久化）
- JWT（Access Token）+ Refresh Token 轮换
- 分层结构：router / handler / service / repository
- SQL 迁移脚本（`backend/migrations`）
- CORS 支持、统一错误返回、环境变量配置

---

## 2. 项目结构

```text
.
├── app/                                    # Android 客户端
│   ├── build.gradle.kts
│   └── src/main
│       ├── AndroidManifest.xml
│       ├── java/com/example/pomodoro
│       │   ├── MainActivity.kt
│       │   ├── PomodoroApp.kt
│       │   ├── ServiceLocator.kt
│       │   ├── domain/Models.kt
│       │   ├── data
│       │   │   ├── local/...
│       │   │   ├── remote/...
│       │   │   ├── repository/...
│       │   │   └── sync/SyncWorker.kt
│       │   ├── service/TimerForegroundService.kt
│       │   └── ui/...
│       └── res/values/...
├── backend/
│   ├── cmd
│   │   ├── migrate/main.go
│   │   └── server/main.go
│   ├── internal
│   │   ├── config/
│   │   ├── db/
│   │   ├── errors/
│   │   ├── handler/
│   │   ├── middleware/
│   │   ├── models/
│   │   ├── repository/
│   │   ├── router/
│   │   └── service/
│   ├── migrations/001_init.sql
│   ├── scripts/init_db.sh
│   ├── .env.example
│   └── go.mod
├── scripts
│   ├── start_backend.sh
│   └── verify_sync_flow.sh
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew
```

---

## 3. Backend 运行方式

### 3.1 环境变量

复制并修改：

```bash
cp backend/.env.example backend/.env
```

可配置项：

- `APP_ADDR`（默认 `:8080`）
- `DB_PATH`（默认 `data/pomodoro.db`）
- `JWT_SECRET`（生产环境必须修改）
- `ACCESS_TOKEN_MINUTES`（默认 15）
- `REFRESH_TOKEN_HOURS`（默认 720）
- `CORS_ORIGINS`（逗号分隔，默认 `*`）

### 3.2 初始化数据库

```bash
./backend/scripts/init_db.sh
```

### 3.3 启动后端（根目录一键）

```bash
./scripts/start_backend.sh
```

---

## 4. Android 客户端运行方式

### 4.1 准备

- JDK 17+
- Android SDK（在 `local.properties` 配置 `sdk.dir`）
- Android Studio（推荐）

`app/build.gradle.kts` 已通过 `buildConfigField` 管理 API 地址：

- `dev`：`DEV_API_BASE_URL`（默认 `http://10.0.2.2:8080/api/v1/`）
- `prod`：`PROD_API_BASE_URL`

你也可以通过 Gradle 参数覆盖：

```bash
./gradlew :app:assembleDevDebug -PDEV_API_BASE_URL=http://10.0.2.2:8080/api/v1/
```

### 4.2 Android Studio

1. 打开根目录工程
2. 选择 `app` 模块
3. 构建变体选择 `devDebug`
4. 运行到模拟器/真机

---

## 5. RESTful API 定义

前缀：`/api/v1`

### 5.1 鉴权

- `POST /auth/register`
  - req: `{ "email": "...", "password": "..." }`
  - resp: `{ userId, accessToken, refreshToken, expiresInSec }`
- `POST /auth/login`
- `POST /auth/refresh`
  - req: `{ "refreshToken": "..." }`
- `POST /auth/logout`

### 5.2 计时状态

- `GET /timer/state`
- `POST /timer/start`
  - req: `{ mode, durationSec, clientVersion }`
- `POST /timer/pause`
- `POST /timer/reset`
- `POST /timer/complete`
  - req: `{ clientVersion }`

成功响应均返回 `TimerState`：

```json
{
  "userId": "...",
  "mode": "focus|short_break|long_break",
  "status": "idle|running|paused",
  "durationSec": 1500,
  "remainingSec": 1488,
  "startedAt": 1730000000,
  "endAt": 1730001500,
  "version": 3,
  "updatedAt": 1730000012
}
```

并发冲突（多设备写入）返回 `409`：

```json
{
  "code": "version_conflict",
  "message": "...",
  "currentState": { ...最新服务端状态... }
}
```

### 5.3 历史记录

- `GET /timer/history?since=<unix>&limit=100`
  - resp: `{ items: [...], serverTime }`

---

## 6. 同步策略（客户端）

- 本地 Room 表：
  - `timer_states`
  - `focus_histories`
  - `pending_actions`（离线动作队列）
- 用户操作（开始/暂停/重置/完成）：
  1. 本地乐观更新
  2. 写入 `pending_actions`
  3. 触发同步回放
- 服务端基于 `clientVersion` 做乐观并发控制：
  - 版本一致：写入成功
  - 版本不一致：返回 409 + `currentState`
- 客户端收到 409：丢弃冲突动作并收敛到服务端状态
- 历史记录采用 `since` 增量拉取
- App 重启后：
  - Room 恢复本地状态
  - `syncNow()` 对账服务端，修正差异

---

## 7. 计时可靠性方案

- 运行中使用 **Foreground Service** 显示通知（状态/剩余时间）
- 核心状态（`endAt`, `version` 等）落地 Room + 服务端持久化
- 即使 App 被杀/重启，重新登录后也可通过：
  - 本地状态恢复
  - 服务端状态拉取
  恢复正在进行的番茄钟
- 服务端 `GetState` 时对已过期运行中的计时做懒结算（自动入历史并回到 idle）

---

## 8. 自我验证（已执行）

执行：

```bash
./scripts/verify_sync_flow.sh
```

脚本覆盖：

1. 注册
2. 双设备登录
3. 开始番茄钟
4. 模拟杀进程/重启后拉取恢复
5. 第二设备同步校验
6. 并发冲突（409）验证
7. 完成计时并验证历史同步
8. Refresh Token 刷新后继续访问
9. CORS 头校验
10. 多用户数据隔离校验

脚本成功标志：`ALL_CHECKS_PASSED`

---

## 9. 常见问题（FAQ）

1. **Android 模拟器访问本机后端失败？**  
   使用 `10.0.2.2` 代替 `localhost`。

2. **构建报 SDK location not found？**  
   在根目录创建 `local.properties` 并设置：
   `sdk.dir=/path/to/Android/Sdk`

3. **生产环境安全建议**  
   - 替换 `JWT_SECRET`
   - 使用 HTTPS
   - 限制 CORS 源
   - 按需接入更严格 token 撤销策略

