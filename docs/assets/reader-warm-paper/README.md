# 温暖纸感阅读主题

版本 `1.47.90` 在正式阅读器的「阅读设置 → 界面样式」中新增「温暖纸感」。经典、书房、临时阅读与浅蓝灰继续保留，默认选择不变。

## 设计参考

| 状态 | 设计图 |
| --- | --- |
| 阅读概览 | [01-overview.png](01-overview.png) |
| 目录抽屉 | [02-contents.png](02-contents.png) |
| 书内搜索 | [03-search.png](03-search.png) |
| 划线列表 | [04-highlights.png](04-highlights.png) |
| 阅读设置 | [05-settings.png](05-settings.png) |
| 选文与笔记 | [06-annotation.png](06-annotation.png) |

实际运行截图与验证记录见 [运行验收](implementation/README.md)。

本次复用现有 Vue 组件、阅读引擎和业务接口，没有新增依赖。纸感主题采用独立的样式作用域与账户排版配置；阅读模式、单双栏、缩进和进度显示仍沿用共用偏好。界面保留真实数据和引擎能力，不使用示例页码或没有实际支持的操作填充设计。
