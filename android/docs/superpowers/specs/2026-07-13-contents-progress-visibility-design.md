# 目录阅读进度卡片显示设置设计

## 目标

允许用户决定目录页面是否显示阅读进度卡片，同时保持现有用户升级后的默认界面不变。

## 设计

- `ReaderSettings` 增加布尔字段 `showContentsProgress`，默认值为 `true`。
- `ReaderSettingsStore` 使用 DataStore Preferences 持久化该字段，未保存时返回 `true`。
- `ReaderViewModel` 收集该设置并提供 setter；取消设置和重置设置时同步恢复该字段。
- “阅读设置 → 排版”在“目录样式”下增加“显示目录阅读进度”开关。
- `ReaderContentsPage` 仅在 `showContentsProgress` 为 `true` 时渲染 `ContentsProgressCard`。
- 设置对所有书籍生效，不新增书籍级配置。

## 验证

- 测试默认设置保持显示，并验证显示判断函数的开关行为。
- 运行 App 全部单元测试、Debug Kotlin 编译和差异检查。
