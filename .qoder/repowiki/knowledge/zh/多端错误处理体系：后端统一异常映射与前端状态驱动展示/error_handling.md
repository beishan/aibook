本仓库采用「后端集中式异常映射 + 前端状态流」的分层错误处理方案，覆盖 Spring Boot 后端、Android 客户端与 Vue3 管理界面三个子工程。

## 后端（Spring Boot）
- **全局异常处理器** `GlobalExceptionHandler` 使用 `@RestControllerAdvice` 集中捕获三类异常并返回统一的 JSON 结构 `{error, message}`：
  - `ResourceNotFoundException` → 404 Not Found
  - `IllegalArgumentException` → 400 Bad Request
  - 兜底 `Exception` → 500 Internal Server Error
- **业务异常类型**集中在 `exception/` 包，目前仅有 `ResourceNotFoundException`，提供带资源名+ID 的便捷构造器。
- **服务层抛错风格不统一**：多处直接 `throw new RuntimeException("xxx")`（如 `AuthService`、`BookListService`、`TagService`），未封装为自定义业务异常；另有少量 `IllegalStateException`、`IllegalArgumentException` 用于参数校验。
- **控制器中硬编码认证失败**：多个 Controller 在鉴权失败时直接 `throw new RuntimeException("未认证")`，未走统一异常通道。

## Android 客户端（Kotlin/Compose）
- **网络层异常**：`OkHttpOpdsTransport` 将 HTTP 非成功响应包装为自定义 `OpdsNetworkException` 向上抛出。
- **ViewModel 层**采用 try-catch + `StateFlow` 状态驱动 UI 的错误展示模式：每个异步操作包裹 `try { ... } catch (e: Exception)`，将 `e.message` 写入 `OpdsUiState.errorMessage` / `statusMessage`，UI 通过读取 StateFlow 显示。
- **无协程 Result/Flow.catch 等函数式错误传播**，全部使用传统 try-catch。
- **Repository 层**同样用 try-catch 包裹文件扫描等 IO 操作，并将异常信息持久化到连接实体的 `errorMessage` 字段。

## Vue3 管理界面
- 未发现专用的错误处理中间件或统一 API 拦截器；错误处理分散在各组件中，依赖 Element Plus 的默认提示机制。

## 设计决策与不足
- 后端仅定义了单一业务异常类型，缺乏按领域划分的异常族，导致大量 `RuntimeException` 裸抛，语义不够明确。
- 缺少统一的错误码（error code）约定，HTTP 状态码与业务错误之间没有一一映射文档。
- Android 端未采用 Kotlin `Result<T>` 或 Flow 的错误能力，错误路径与正常路径交织在 try-catch 块中，可读性一般。
- 前后端错误消息格式不一致：后端返回 `{error, message}`，前端各自解析，未定义跨端契约。

## 开发者应遵循的规则
1. 在后端新增业务异常时，优先扩展 `exception/` 下的自定义异常类，而非直接抛 `RuntimeException`。
2. 所有 Controller 中的认证/授权失败应通过统一的鉴权过滤器或自定义异常抛出，由 `GlobalExceptionHandler` 统一映射。
3. Android 侧建议逐步迁移到 `Result<T>` 或 `Flow.catch`，减少 try-catch 嵌套，使错误路径与正常路径分离。
4. 前后端应约定统一的错误响应体结构（如包含 code/message/description），并在前端建立统一的 API 拦截器进行解析和提示。