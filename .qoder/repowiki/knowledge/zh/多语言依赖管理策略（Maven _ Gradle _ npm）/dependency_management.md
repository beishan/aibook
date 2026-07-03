本仓库为多语言聚合项目，包含后端 Spring Boot、前端 Vue3 与 Android 阅读器三个子工程，各子工程采用各自生态的包管理器进行依赖声明与解析，整体未引入统一的依赖治理工具。

## 1. 后端（Java/Spring Boot）— Maven
- 包管理器：Maven，基于 spring-boot-starter-parent:3.2.0 作为父 POM，统一继承 Spring Boot BOM 的版本对齐。
- 版本管理方式：
  - 通过 properties 集中声明公共版本号（如 jjwt.version=0.12.3），在多个依赖中引用变量，避免散落的硬编码。
  - 其余依赖直接写死具体版本（如 jsoup 1.17.2、mapstruct 1.5.5.Final）。
- 构建插件：
  - spring-boot-maven-plugin 打包时排除 Lombok。
  - maven-compiler-plugin 显式配置注解处理器路径（Lombok + MapStruct Processor），确保编译期代码生成稳定。
- 私有/本地依赖：未发现自定义 repositories 或私有 Nexus/Artifactory 配置，所有依赖均从 Maven Central 拉取。
- 锁定文件：无 pom.xml.lock 等锁定机制，依赖版本由开发者手动维护。

## 2. Android（Kotlin/Compose）— Gradle Kotlin DSL
- 包管理器：Gradle 8.13 + Kotlin 2.2.21，采用多模块结构（:app, :core:model, :core:network, :core:data, :core:reader）。
- 版本集中化：
  - 根级 build.gradle.kts 使用 plugins { id(...) version "..." apply false } 集中声明所有插件版本。
  - app/build.gradle.kts 通过 platform("androidx.compose:compose-bom:2025.01.01") 引入 Compose BOM，统一管理 Compose 组件版本。
  - 其他第三方库（OkHttp 5.1.0、Retrofit 3.0.0、jsoup 1.18.1、Readium 3.1.0）直接在模块内声明版本。
- 仓库策略：
  - settings.gradle.kts 启用 dependencyResolutionManagement 并设置 RepositoriesMode.FAIL_ON_PROJECT_REPOS，禁止子模块自行声明仓库，强制统一来源。
  - 仓库顺序：local-maven → google() → mavenCentral()，优先使用本地私有仓库。
  - 存在 android/local-maven/org/jsoup/jsoup/1.18.1/ 目录，说明对个别依赖（jsoup）采用本地 Maven 仓库缓存/替换策略。
- JDK 版本：gradle.properties 指定 org.gradle.java.home 指向 OpenJDK 21，Android 模块编译目标为 Java 17。
- 锁定文件：无 gradle.lockfile 或类似锁定机制。

## 3. 前端（Vue3/Vite）— npm
- 包管理器：npm，使用 package.json 声明依赖，配合 package-lock.json（lockfileVersion 3）锁定精确版本树。
- 依赖分类：
  - dependencies：运行时依赖（axios、element-plus、epubjs、pinia、vue、vue-router）。
  - devDependencies：构建与类型检查工具（@vitejs/plugin-vue、typescript、vite、vue-tsc）。
- 版本策略：全部使用 ^ 语义化版本范围，允许小版本自动升级；实际安装结果由 package-lock.json 固化。
- 私有源/代理：未发现 .npmrc 或 registry 配置，默认使用 npm 官方源。

## 4. 容器编排中的依赖服务
- docker/docker-compose.yml 通过 Docker Compose 编排 PostgreSQL 16、Redis 7、MinIO 等外部依赖服务，以镜像版本而非包管理器管理这些运行时依赖。
- 敏感信息（数据库密码、JWT 密钥、MinIO 凭据）通过环境变量注入，不硬编码到配置文件中。

## 5. 开发者应遵循的规则
- 后端：新增依赖时在 properties 中集中声明版本号，保持版本一致性；仅在必要时覆盖父 POM 版本。
- Android：
  - 所有插件版本必须在根 build.gradle.kts 中声明，禁止在子模块重复定义。
  - 新增仓库必须通过 settings.gradle.kts 的 dependencyResolutionManagement.repositories 注册，不得在子模块内声明仓库。
  - 优先使用 Compose BOM 和已存在的 BOM 管理相关依赖版本。
  - 需要离线/私有依赖时，放入 android/local-maven 并按坐标组织目录。
- 前端：升级依赖后务必提交更新后的 package-lock.json，保证 CI 与本地构建一致。
- 通用：当前仓库未引入 Dependabot/Renovate 等自动化升级工具，建议后续补充以跟踪安全漏洞与版本过期。