# Codex UI Prototype Pack

这是为 Android 小说阅读器准备的 Codex 友好型 UI 原型包。

## 推荐技术栈
- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- ViewModel + StateFlow
- Room（本地书籍/收藏/书架）
- Retrofit/OkHttp（后端与 OPDS）
- Coil（封面）
- WorkManager（下载/同步/扫描长任务）

## 目录
- `design-tokens.json`：颜色、圆角、间距、字体等全局设计 Token
- `navigation.json`：底部导航和完整页面路由
- `components/`：可复用 UI 组件规格
- `screens/`：每个页面的结构化原型
- `docs/codex-prompt.md`：推荐给 Codex 的实现提示词
- visuals 目录为每个页面的视觉效果图

## Codex 实现要求
1. 所有页面严格复用 Design Tokens，不允许每个页面单独硬编码颜色和间距。
2. Local / OPDS / Backend 在 UI 层统一抽象为 Book + BookSource。
3. 书架只展示已加入书架的书；书城负责浏览所有来源。
4. “从书架移除”与“删除书籍/文件”严格区分。
5. OPDS 与后端网络页面必须提供 Loading / Empty / Error 状态。
6. 所有列表页面优先抽成通用 `BookCollectionScreen` / `BookGrid` / `BookListItem`。
7. UI 页面先用 Preview + Fake Data 实现，完成视觉后再接 Repository。
8. 所有页面设计严格遵守 visuals文件夹下的UI图
9. preview.html 文件提供了所有预览图的合集
10. visual-index.md 文件是效果图的索引


