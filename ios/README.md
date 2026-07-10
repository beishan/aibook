# 汗牛充栋 · iOS 客户端

汗牛充栋图书管理系统的 iOS 原生客户端，与安卓客户端功能完全对齐。

## 技术栈

| 层次 | 技术选型 |
|---|---|
| 语言 | Swift 5.10+ |
| UI | SwiftUI + @Observable |
| 数据库 | SwiftData (iOS 17+) |
| 网络 | URLSession + Codable |
| EPUB | Readium Swift Toolkit 3.x |
| HTML 解析 | SwiftSoup |
| 图片加载 | Kingfisher |

## 项目结构

```
AiBook/
├── App/                    # 应用入口
├── Core/
│   ├── Model/              # 纯数据模型
│   ├── Reader/             # 阅读器模型
│   ├── Network/            # 网络层 (API + OPDS)
│   ├── Data/               # 持久化层 (SwiftData + Repository + Prefs)
│   └── DI/                 # 依赖注入
├── Feature/
│   ├── Shelf/              # 书架
│   ├── Store/              # 书城
│   ├── Opds/               # 发现 / OPDS
│   ├── Reader/             # 阅读器
│   ├── Importer/           # 本地书籍导入
│   └── Settings/           # 设置
├── Navigation/             # 路由定义
└── UI/                     # 主题、通用组件
```

## 开发环境

- Xcode 16+
- iOS 17+ Deployment Target
- Swift Package Manager 依赖管理

## 依赖库

- [Readium Swift Toolkit](https://github.com/readium/swift-toolkit) — EPUB 解析
- [SwiftSoup](https://github.com/scinfu/SwiftSoup) — HTML 解析
- [Kingfisher](https://github.com/onevcat/Kingfisher) — 图片加载缓存
