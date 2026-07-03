本项目采用「后端 Spring Boot + 前端 Vue3 + Android Kotlin」三端并行架构，各子工程使用各自原生构建工具独立编译，再通过 Docker Compose 统一编排依赖服务与产物运行。

## 1. 构建系统与工具链

- **后端（backend）**：Maven 管理，基于 `spring-boot-starter-parent:3.2.0`，Java 21；通过 `spring-boot-maven-plugin` 打包为可执行 JAR，启用 Lombok + MapStruct 注解处理器。
- **Android（android）**：Gradle Kotlin DSL 多模块工程，根 `build.gradle.kts` 集中声明插件版本（AGP 8.13、Kotlin 2.2.21、Compose BOM 2025.01），`app` 模块聚合 `core:model`、`core:network`、`core:data`、`core:reader` 四个 library 模块，Compose UI + Readium EPUB 阅读器。
- **前端（frontend）**：Vite 5 + Vue 3 + TypeScript，`package.json` 提供 `dev/build/preview/lint` 脚本，开发时通过 Vite proxy 转发 `/api` 到 `localhost:8080`。

## 2. 容器化与编排

- **Dockerfile**：后端与前端均采用两阶段构建——builder 镜像负责下载依赖并编译，runtime 镜像仅包含运行时（JRE 21 Alpine / Nginx Alpine），非 root 用户运行。
- **docker-compose.yml**：在 `docker/` 目录下定义五服务拓扑：PostgreSQL 16、Redis 7、MinIO、backend、frontend，通过自定义 bridge 网络 `aibook-network` 互通，所有外部凭据通过 `.env` 注入（默认密码 `memoryvault`）。
- 数据持久化通过命名卷 `postgres_data`、`redis_data`、`minio_data`、`uploads_data` 挂载；后端额外将宿主机目录映射为 `/scanfolder` 供扫描。

## 3. 关键约定与约束

- 版本集中管理：Android 插件/Kotlin/Compose 版本集中在根 `build.gradle.kts`；后端依赖版本集中在 `pom.xml` 的 `<properties>` 与父 POM。
- 环境变量驱动配置：数据库密码、JWT Secret、MinIO 凭据一律通过 `${VAR:-default}` 形式注入，禁止硬编码。
- 健康检查：compose 中每个依赖服务均配置 `healthcheck`，后端 `depends_on` 以 `condition: service_healthy` 确保启动顺序。
- 无 CI/CD 流水线：仓库未检出 `.github/workflows` 等 CI 文件，本地 `docker compose up --build` 是主要交付路径。

## 4. 开发者须知

- 新增后端依赖 → 编辑 `backend/pom.xml`，必要时同步更新 Dockerfile builder 缓存层。
- 新增 Android 模块 → 在根 `settings.gradle.kts` 注册并在 `app/build.gradle.kts` 添加 `implementation(project(":xxx"))`。
- 修改前端代理或端口 → 调整 `frontend/vite.config.ts` 的 `server.proxy` 与 `port`。
- 本地联调推荐：先 `docker compose up postgres redis minio`，再分别启动后端与前端，避免容器间网络问题。