你正在实现一个 Android 阅读器。请先阅读当前目录中的 README.md、design-system.md、navigation.md、data-model.md 和 screens/，并把 images/ 中的截图作为视觉参考。不要复制任何第三方产品的品牌、文案、封面、书名或其他受版权保护的内容；截图仅用于布局、层级、留白和交互密度参考。

技术约束：Kotlin + Jetpack Compose + Material 3 + Navigation Compose + Room + DataStore + WorkManager。使用单向数据流、ViewModel、Repository 分层。所有列表需要有 loading、empty、error 状态。使用假数据/本地种子数据让 UI 能立即预览。

请按以下阶段完成，并在每一阶段后说明修改的文件、可运行方式和未实现项：

1. 初始化项目架构：主题 token、导航骨架、底部四个 Tab、核心数据模型和 Room schema。
2. 实现一级页面：书架、书城、发现、设置；视觉遵循 images/01、02、04、17。
3. 实现二级页面：分类浏览、书籍详情、搜索、OPDS 数据源列表/添加、扫描目录管理。
4. 实现阅读器 UI：正文占位渲染、目录、进度、阅读设置、书签/笔记。
5. 接入真实能力：SAF 文件导入/目录选择、扫描 Worker、OPDS 客户端、下载与缓存。
6. 添加测试：Repository 单测、ViewModel 状态测试、导航 UI 测试；在没有网络时保证本地书城仍可用。

产品规则：书架只显示用户主动加入的书；书城聚合本地与已启用 OPDS 源；本地扫描导入书籍后只进入书城，不自动加入书架；删除本地文件与移出书架必须分开。
