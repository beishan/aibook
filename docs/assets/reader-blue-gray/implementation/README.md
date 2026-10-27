# 浅蓝灰阅读工作台 · 运行验收

版本：`1.47.89`，日期：2026-10-27。

本目录为实际 Vue 阅读器在 Chrome 中运行的截图，使用本地模拟接口和测试书籍，封面为空时显示系统占位。上一级目录保存的是设计参考图。书籍数量、日期、正文与进度属于测试数据。

## 界面截图

| 状态 | 运行截图 |
| --- | --- |
| 阅读与目录 | [01-reading.png](01-reading.png) |
| 书内搜索 | [02-search.png](02-search.png) |
| 书签 | [03-bookmarks.png](03-bookmarks.png) |
| 笔记编辑 | [04-notes.png](04-notes.png) |
| 划线与选文 | [05-highlights.png](05-highlights.png) |
| 阅读设置 | [06-settings.png](06-settings.png) |
| 全屏 | [07-fullscreen.png](07-fullscreen.png) |
| 从新主题切回经典 | [08-classic-preserved.png](08-classic-preserved.png) |
| 夜间 | [09-night.png](09-night.png) |
| 手机 320px | [mobile-320.png](mobile-320.png) |
| 手机 390px | [mobile-390.png](mobile-390.png) |
| 平板 768px | [mobile-768.png](mobile-768.png) |
| EPUB | [epub-reading.png](epub-reading.png) |
| PDF | [pdf-reading.png](pdf-reading.png) |

## 原样式隔离验证

将修改前提交 `9c32cc25` 的前端放到临时目录，用相同接口数据、字体、Chrome、减少动画偏好和 1536×1024 视口，与本次实现对比 RGBA 像素。该结果验证初始阅读页的外观隔离；交互与格式能力另由浏览器回归覆盖。

| 原样式 | 改动后截图 | 差异像素 |
| --- | --- | --- |
| 经典 | [legacy-classic.png](legacy-classic.png) | 0 / 1,572,864 |
| 书房 | [legacy-readingRoom.png](legacy-readingRoom.png) | 0 / 1,572,864 |
| 临时阅读 | [legacy-trialReader.png](legacy-trialReader.png) | 0 / 1,572,864 |

## 复验与部署

- 前端：`npm run build`、`npm run test:user-guide`、`npm run test:reader-readium-progress`。
- 浏览器：先启动本地前端，再执行 `npm run test:reader-blue-gray`；遵循仓库其他 UI 测试的环境变量约定，可用 `MOBILE_BASE_URL`、`MOBILE_PLAYWRIGHT_PATH` 和 `MOBILE_BROWSER_CHANNEL` 指定测试环境。截图默认输出到 `/tmp/reader-blue-gray-review`，也可用 `READER_SCREENSHOT_DIR` 指定。仅生成旧样式截图可设置 `READER_LEGACY_ONLY=1`。
- 后端：在 `backend` 执行 `mvn -Dtest=UserServiceTest test`。
- 部署时同步更新前后端：后端新增 `blueGray` 外观允许值与独立排版配置校验，沿用现有偏好 JSON 字段，无数据库迁移。
- 原有独立类型检查工具存在兼容问题：已安装的 vue-tsc 1.8 不能启动 TypeScript 5.9 的检查，修改前副本同样失败。本次未改依赖；生产构建、浏览器回归和上述其他检查通过，未在 NAS 实机联调。

字体、书籍原始版式、PDF 固定页面和不同设备的字体渲染会影响实际显示；主题保留真实阅读数据和原格式能力，未以设计图中的示例数字替代实际页码。
