本仓库采用「后端集中式 + 前端/客户端本地化」的分层配置体系，覆盖应用启动参数、运行时可持久化配置与用户偏好三类场景。

## 1. 后端（Spring Boot）

- **静态配置**：`backend/src/main/resources/application.yml` 使用 Spring `@ConfigurationProperties` 风格键名，通过 `${ENV_VAR:default}` 占位符注入环境变量，如数据库 URL、Redis 主机、MinIO endpoint、JWT secret、上传路径等；默认值面向 Docker Compose 开发环境。
- **Docker 编排**：根目录 `.env` 与 `docker/.env` 提供统一的环境变量清单，被 `docker-compose.yml` 注入到各容器，形成「一份 .env → 多服务共享」的部署约定。
- **运行时配置（动态）**：通过 JPA 实体 `SystemConfig`（表 `system_config`）、`SystemConfigRepository`、`SystemConfigService` 与 `SystemConfigController` 暴露 `/api/config/*` REST 接口，支持按前缀批量读写（当前仅实现 `scraper.*` 命名空间），用于在运行期调整爬虫开关、优先级等策略，无需重启。
- **组件级配置类**：`AppConfig`、`JacksonConfig`、`RedisConfig` 以 `@Configuration` + `@Bean` 方式注册 RestTemplate、ObjectMapper、RedisTemplate，序列化策略集中在 RedisConfig 中定义。

## 2. Android 客户端

- **服务端连接与认证**：`ServerConfigStore` 基于 `androidx.datastore.preferences` 持久化服务器地址、JWT token、用户名、邮箱、是否仅 WiFi 同步等，所有字段以 Kotlin Flow 暴露，供 UI 与网络层响应式消费。
- **阅读器偏好**：`ReaderSettingsStore` 将字体缩放、行高、主题、段落间距、对齐方式、翻页模式、自动亮度、常亮等阅读体验设置存入独立 DataStore，同样以 Flow 驱动界面更新。
- 两个 Store 均遵循「Key 对象集中声明 + Flow 只读 + suspend 写入」的统一模式。

## 3. 前端管理界面（Vue3）

- **主题与布局**：`theme.ts` Pinia store 将当前主题 ID 写入 `localStorage('ai-book-theme')`，并通过 `document.documentElement.dataset.theme` 切换 CSS 变量，实现无刷新换肤。
- **刮削器配置**：`config.ts` 调用后端 `/api/config/scraper` 获取/更新 scraper 相关键值对，作为前端唯一与后端运行时配置交互的入口。

## 4. 架构约定与规则

| 层级 | 存储位置 | 典型用途 | 访问方式 |
|------|----------|----------|----------|
| 启动参数 | `application.yml` + 环境变量 | 数据库、Redis、MinIO、JWT、扫描任务等 | Spring 自动绑定 |
| 运行时配置 | DB `system_config` 表 | 爬虫开关、优先级等可热更新的业务策略 | `/api/config/*` REST |
| 客户端偏好 | Android DataStore / 浏览器 localStorage | 服务器地址、token、阅读排版、UI 主题 | DataStore Flow / Pinia store |

- 环境变量命名遵循 `SPRING_*`、`REDIS_*`、`MINIO_*`、`JWT_*`、`UPLOAD_PATH` 等大写前缀，便于跨服务识别。
- 运行时配置键采用「命名空间前缀.子键」（如 `scraper.douban.enabled`），通过 Controller 层强制过滤前缀，防止越权修改。
- 新增配置项应优先走环境变量注入；仅在需要「不重启即可生效」的业务开关时才放入 `system_config` 表。
- Android 侧新增偏好需同时补充 `Keys` 常量、Flow 读取与 `suspend` 写入方法，保持与现有 Store 一致。