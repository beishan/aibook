# 温暖纸感 · 运行验收

日期：2026-10-28，版本：`1.47.90`。

以下为真实 Vue 界面在 Chrome 中运行的截图，使用本地模拟接口和样例书籍；书名、正文、页码、记录数量属于测试数据。上一级目录保存设计参考。

## 界面截图

| 状态 | 运行截图 |
| --- | --- |
| 阅读概览 | [01-overview.png](01-overview.png) |
| 左侧目录 | [02-contents.png](02-contents.png) |
| 书内搜索 | [03-search.png](03-search.png) |
| 右侧书签 | [04-bookmarks.png](04-bookmarks.png) |
| 划线列表 | [05-highlights.png](05-highlights.png) |
| 批注编辑 | [06-annotation.png](06-annotation.png) |
| 选文工具 | [07-selection.png](07-selection.png) |
| 阅读设置 | [08-settings.png](08-settings.png) |
| 墨夜黑 | [09-night.png](09-night.png) |
| 全屏 | [10-fullscreen.png](10-fullscreen.png) |
| 手机 320px | [mobile-320.png](mobile-320.png) |
| 手机 390px | [mobile-390.png](mobile-390.png) |
| 平板 768px | [mobile-768.png](mobile-768.png) |
| Markdown | [format-md.png](format-md.png) |
| 在线章节 | [format-structured.png](format-structured.png) |
| HTML | [format-html.png](format-html.png) |
| EPUB | [format-epub.png](format-epub.png) |
| PDF | [format-pdf.png](format-pdf.png) |

## 既有主题隔离

以修改前提交 `366aff31` 的前端副本为基准，在相同模拟接口、字体、Chrome、减少动画偏好与 1536×1024 视口下比较 RGBA 像素。结果验证初始阅读页外观；交互另由浏览器回归覆盖。

| 主题 | 改动后截图 | 差异像素 |
| --- | --- | --- |
| 经典 | [legacy-classic.png](legacy-classic.png) | 0 / 1,572,864 |
| 书房 | [legacy-readingRoom.png](legacy-readingRoom.png) | 0 / 1,572,864 |
| 临时阅读 | [legacy-trialReader.png](legacy-trialReader.png) | 0 / 1,572,864 |
| 浅蓝灰 | [legacy-blueGray.png](legacy-blueGray.png) | 0 / 1,572,864 |

机器可读结果见 [legacy-comparison.json](legacy-comparison.json)。

## 复验

- 前端：`npm run build`、`npm run test:user-guide`、`npm run test:reader-readium-progress`。
- 浏览器：先启动本地前端，再执行 `npm run test:reader-warm-paper` 和 `npm run test:reader-blue-gray`。与仓库原有 UI 检查一样，可以通过 `MOBILE_BASE_URL`、`MOBILE_PLAYWRIGHT_PATH`、`MOBILE_BROWSER_CHANNEL` 指定环境。纸感截图默认保存到 `/tmp/reader-warm-paper-review`，可用 `READER_SCREENSHOT_DIR` 修改。
- 后端：`mvn -Dtest=UserServiceTest test`，本次 31 项通过，包括独立排版持久化及非法配置拒绝。
- 浏览器检查覆盖面板位置、目录定位、书内搜索、书签、笔记编辑、选文划线、真实字体切换、对齐、账户偏好与刷新恢复、主题切回、夜间对比度、全屏、窄屏和六种阅读格式；浅蓝灰原有完整交互回归也已通过。

## 部署与限制

- 同步更新前后端。后端新增 `warmPaper` 允许值、独立排版及对齐校验，继续使用已有偏好 JSON 字段，无数据库迁移和新增依赖。
- Readium 不支持居中对齐，设置面板仅提供其支持的左对齐与两端对齐；切回稳定引擎可使用居中。PDF 保持文件固定版式。模板示例中的「专注」与笔记标签未作为无实际支持的控件加入界面。
- 独立类型检查仍受既有 vue-tsc 1.8 / TypeScript 5.9 启动兼容问题影响（supportedTSExtensions 匹配失败）。生产构建和其他回归已通过，依赖未调整；未进行 NAS 实机联调。
- 不同设备字体和书籍原始版式会影响显示；可使用系统已有的字体管理功能选择需要的阅读字体。
