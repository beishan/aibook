## 系统概览

本仓库采用多端架构，日志体系按端拆分：后端使用 Spring Boot + Lombok + SLF4J（默认绑定 Logback），Android 客户端通过 OkHttp HttpLoggingInterceptor 输出 HTTP 报文，前端管理界面未引入专用日志框架。

## 后端日志（Spring Boot / Lombok）

- 框架与依赖：通过 Lombok 注解 @Slf4j 在每个 Controller/Filter 中注入 log 实例；日志门面为 SLF4J，由 Spring Boot 自动装配 Logback 作为实现。
- 级别配置：在 backend/src/main/resources/application.yml 的 logging.level 下将 com.aibook 与 org.springframework.security 设为 DEBUG，其余包走 Spring Boot 默认级别。
- 使用模式：控制器层统一以 @Slf4j 标注，业务异常集中在 GlobalExceptionHandler 中用 log.debug/log.error 记录；文件浏览、上传等关键路径使用 log.warn 标记安全拒绝或资源不存在。
- 结构化字段：当前全部使用字符串模板拼接，尚未引入 JSON 结构化日志字段。

## Android 客户端日志

- HTTP 请求日志：ApiServiceFactory.createOkHttpClient(enableLogging) 支持可选地挂载 HttpLoggingInterceptor(Level.BODY)，用于调试网络层请求/响应体。
- 第三方库日志：EPUB 解析器 Readium 通过 ListWarningLogger 收集警告，未接入应用级日志中心。
- 其他日志：未发现统一的 Android Logger 封装或日志级别开关。

## 前端管理界面

- 未发现引入任何日志框架，调试主要依赖浏览器控制台。

## 开发者约定与建议

1. 后端新增类优先使用 @Slf4j 注入 log，对异常路径使用 error，对可恢复问题使用 warn，对入口/出口使用 info，诊断信息使用 debug。
2. 建议逐步引入 JSON 格式输出，为每条日志附加 traceId、userId、bookId 等关键字段，便于集中采集与检索。
3. 避免在日志中直接打印密码、JWT、MinIO secret-key 等敏感值。
4. 仅在生产构建外启用 enableLogging=true，避免 BODY 级别日志影响性能与隐私。
5. 如需追踪用户操作，可在前端补充轻量 logger，并遵循与后端一致的 traceId 传递约定。

## 关键文件

- backend/src/main/resources/application.yml — 全局日志级别配置
- backend/src/main/java/com/aibook/controller/GlobalExceptionHandler.java — 全局异常日志记录
- backend/src/main/java/com/aibook/controller/FileBrowseController.java — 典型 warn/error 使用示例
- android/core/network/src/main/kotlin/com/aibook/android/core/network/api/ApiServiceFactory.kt — OkHttp 网络日志拦截器
- android/app/src/main/kotlin/com/aibook/android/feature/reader/ReadiumEpubReader.kt — 第三方库 ListWarningLogger 集成