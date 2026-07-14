# 汗牛充栋 iOS 工程基线设计

## 1. 背景与目标

仓库已经包含 `requires_ios.md`、一套 SwiftUI/SwiftData 业务源码以及一个 Xcode 工程，但当前工程位于嵌套 Git 仓库中，主仓库又将其记录成缺失 `.gitmodules` 配置的 Gitlink。该状态会造成源码、工程配置和版本历史分离，也不利于稳定构建与多人协作。

本设计的目标是保留现有 iOS 实现，将其整理成由主仓库直接管理、使用 Xcode 16+ 与 Swift 6、面向 iOS 17 的可构建工程基线。首个里程碑关注工程健康、并发安全和基础测试，不扩展业务范围。

## 2. 已确认决策

- 完善现有客户端，不重新开发。
- 第一个可验收里程碑是工程基线。
- 以 Xcode 16+ 和 Swift 6 为唯一正式工具链标准。
- 主仓库直接管理全部 iOS 源码和 Xcode 工程。
- 移除嵌套 Git 仓库及失效 Gitlink，同时保留现有未提交工程配置和业务源码内容。
- 最低系统版本为 iOS 17。
- 采用原地规范化方案，不在当前里程碑拆分 Swift Package。

## 3. 目标目录结构

```text
ios/
├── AiBook.xcodeproj/
├── AiBook/
│   ├── App/
│   ├── Core/
│   │   ├── Data/
│   │   ├── DI/
│   │   ├── Model/
│   │   ├── Network/
│   │   └── Reader/
│   ├── Feature/
│   ├── Navigation/
│   ├── Resources/
│   └── UI/
├── AiBookTests/
├── AiBookUITests/
└── README.md
```

主仓库直接跟踪上述文件。工程不再包含嵌套 `.git`，也不再把 `ios/AiBook/AiBook` 作为子模块或 Gitlink。迁移会保留现有 Xcode 工程的未提交配置；Xcode 模板残留 `Item.swift` 在确认没有业务依赖后移除。

工程优先采用 Xcode 文件系统同步分组，减少新增源码时直接编辑 `project.pbxproj` 的需要。业务源码只属于 App Target；单元测试和 UI 测试分别只属于对应测试 Target。Bundle Identifier 与签名团队等机器相关设置不写死，命令行构建验证关闭代码签名。

## 4. 架构与组件边界

首个里程碑不重写现有业务架构，组件职责保持如下：

- `App`：创建依赖容器、注入应用环境并承载四个主 Tab。
- `Feature`：页面和 ViewModel，只处理界面状态及用户操作。
- `Core/Model`：纯 Swift 值类型，不依赖 SwiftUI、SwiftData 或网络层。
- `Core/Data`：SwiftData 实体及 Repository，负责本地持久化。
- `Core/Network`：URLSession、后端 API 与 OPDS 请求。
- `Core/Reader`：书籍解析以及章节、分页等阅读模型。
- `Core/DI`：集中装配依赖，不承载业务逻辑。

标准数据流为：

```text
SwiftUI 页面 → ViewModel → Repository/API/Reader
             ←       领域模型与状态       ←
```

Repository 不向 Feature 层暴露 SwiftData Entity，而是返回领域模型，从而避免持久化对象跨并发域传播。`ServiceLocator` 在当前里程碑继续保留，但仅承担依赖装配；是否替换为细粒度依赖注入留给后续重构评估。

## 5. Swift 6 并发模型

- UI、ViewModel 和 `ModelContext` 访问统一隔离到 `@MainActor`。
- 网络请求、书籍解析与文件操作使用 `async/await`，耗时任务不得阻塞主线程。
- 跨并发域传递的领域值类型明确满足 `Sendable`。
- 不通过大范围 `@unchecked Sendable` 压制编译器诊断。确需使用时，必须局部声明并记录安全依据。
- Swift Language Mode 设置为 Swift 6，并启用严格并发检查。
- 当前机器的旧版 Xcode 构建结果只能用于辅助诊断，不能作为 Swift 6 验收证据。

## 6. 错误处理与诊断

- SwiftData 持久化容器初始化失败时，Debug 环境记录完整上下文，并尝试使用内存容器使应用进入可诊断状态。只有依赖图无法建立等不可恢复错误才终止启动。
- 文件、网络和解析失败转换为明确的领域错误，由 Feature 层呈现中文、可操作的提示。
- 日志不得包含密码、Token 或完整授权请求头。
- Debug 构建保留底层错误上下文；Release 界面只展示用户可理解的信息。
- 工程路径、Target Membership、资源引用和入口冲突必须在构建阶段暴露，不能以运行时兜底掩盖。

## 7. 测试与验收

### 7.1 自动化测试

- 为领域模型的关键转换与边界值补充单元测试。
- 为书架排序规则补充稳定排序及空值场景测试。
- 为导入格式识别和导入策略补充支持、不支持与重复文件场景测试。
- 添加 UI 启动冒烟测试，验证应用成功启动且“书架、书城、发现、设置”四个入口存在。

网络、SwiftData 和文件系统边界应通过协议或可注入依赖隔离，使纯逻辑测试不依赖真实 NAS、网络或用户目录。

### 7.2 完成标准

- 使用 Xcode 16+、Swift 6、iOS 17 模拟器目标完成 Debug 构建。
- 应用可以启动并展示四个主入口。
- 不存在重复 `@main`、缺失源码引用、嵌套 Git 或失效子模块。
- Swift 6 编译错误清零；本里程碑新增代码不产生并发安全警告。
- 单元测试和 UI 冒烟测试通过。
- `ios/README.md` 写明所需 Xcode 版本、打开工程方式、构建命令和测试命令。
- 若执行环境没有 Xcode 16，交付记录必须明确列出未执行项及其复验命令，不得宣称 Swift 6 构建已经通过。

## 8. 迁移顺序

1. 备份并比对嵌套工程的未提交配置，确认需要保留的内容。
2. 将 Xcode 工程、测试代码和资源提升到统一的 `ios/` 结构。
3. 移除主仓库中的 Gitlink 记录和嵌套 `.git`，再将真实文件纳入主仓库。
4. 修复工程文件引用、Target Membership、资源引用和 Build Settings。
5. 移除无业务依赖的 Xcode 模板残留。
6. 切换 Swift 6 与严格并发检查，逐项解决类型和 Actor 隔离问题。
7. 补充单元测试、UI 冒烟测试和开发文档。
8. 执行可用环境内的验证，并记录必须在 Xcode 16 环境复验的命令。

迁移过程中不得覆盖现有业务源码或丢失嵌套工程的未提交配置。每个结构变更完成后都要检查 Git 状态和 Xcode 文件引用，以便快速定位回归。

## 9. 本里程碑范围外事项

- 重做现有 UI 或视觉系统。
- 新增 Android 端尚不存在的业务功能。
- 完整修复 EPUB、PDF 等格式的阅读体验。
- NAS 登录、下载和阅读进度同步的端到端联调。
- 引入 Readium、Kingfisher 等尚未实际接入的第三方依赖。
- Swift Package 模块化拆分。
- App Store 签名、证书、归档和发布配置。

## 10. 后续里程碑

```text
工程基线
→ 本地导入与阅读闭环
→ OPDS 下载闭环
→ NAS 登录与进度同步
→ iPad 适配、性能优化与发布准备
```

每个后续里程碑都应单独确认设计和验收范围，避免工程整理与业务扩展互相干扰。
