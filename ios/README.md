# 汗牛充栋 · iOS 客户端

汗牛充栋图书管理系统的 iOS 原生客户端，与安卓客户端功能完全对齐。

## 技术栈

| 层次 | 技术选型 |
|---|---|
| 语言 | Swift 6 |
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

## 本地开发

1. 使用 Xcode 16 或更高版本打开 `ios/AiBook.xcodeproj`。
2. 在 `AiBook` Target 的 Signing & Capabilities 中选择自己的开发团队，并按需修改 Bundle Identifier。
3. 选择 iOS 17 或更高版本的模拟器，运行 `AiBook` Scheme。

也可以在仓库根目录执行：

```bash
# 编译应用和测试目标
xcodebuild build-for-testing \
  -project ios/AiBook.xcodeproj \
  -scheme AiBook \
  -destination 'platform=iOS Simulator,name=iPhone 15 Pro' \
  CODE_SIGNING_ALLOWED=NO

# 运行单元测试和 UI 冒烟测试
xcodebuild test \
  -project ios/AiBook.xcodeproj \
  -scheme AiBook \
  -destination 'platform=iOS Simulator,name=iPhone 15 Pro' \
  CODE_SIGNING_ALLOWED=NO
```

客户端连接私有服务器时，在“设置 → 同步与连接”中填写局域网服务地址和账号。测试账号、生产凭据与签名证书不得提交到仓库。

## 依赖库

- [Readium Swift Toolkit](https://github.com/readium/swift-toolkit) — EPUB 解析
- [SwiftSoup](https://github.com/scinfu/SwiftSoup) — HTML 解析
- [Kingfisher](https://github.com/onevcat/Kingfisher) — 图片加载缓存
