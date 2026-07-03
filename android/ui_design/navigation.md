# 导航与信息架构

## 一级 Tab

| Route | 名称 | 责任 |
|---|---|---|
| `bookshelf` | 书架 | 当前阅读、书架管理、继续阅读 |
| `bookstore` | 书城 | 本地 + OPDS 聚合浏览、分类、搜索、详情 |
| `discover` | 发现 | 导入本地书、扫描目录、OPDS 数据源管理 |
| `settings` | 设置 | 应用与阅读偏好、缓存、隐私、关于 |

## 关键二级页面

| Route | 页面 | 入口 |
|---|---|---|
| `book/{bookId}` | 书籍详情 | 书城列表、搜索结果、书架更多菜单 |
| `reader/{bookId}` | 阅读界面 | 书架继续阅读、书籍详情开始阅读 |
| `reader/{bookId}/toc` | 目录 | 阅读器底部工具栏 |
| `reader/{bookId}/settings` | 阅读设置 | 阅读器底部工具栏 |
| `search?query=` | 全局搜索 | 书城/发现顶部搜索 |
| `category/{categoryId}` | 分类浏览 | 书城分类入口 |
| `discover/import` | 主动导入 | 发现页“立即导入文件” |
| `discover/directories` | 扫描目录管理 | 发现/设置 |
| `discover/opds` | OPDS 数据源 | 发现页 |
| `discover/opds/add` | 添加 OPDS 源 | OPDS 数据源页 |
| `opds/{sourceId}` | OPDS 目录浏览 | OPDS 数据源卡片 |
| `notes` | 笔记与书签 | 阅读器更多菜单 / 书籍详情 |
| `settings/theme` | 页面主题 | 设置 |
| `settings/storage` | 存储与缓存 | 设置 |
| `settings/privacy` | 隐私与权限 | 设置 |
| `settings/about` | 关于 | 设置 |

## 核心业务规则

1. **书架不等于书城。** 书架只显示用户主动加入并保留的书；书城是可发现的全量目录。
2. 本地导入完成后，书籍进入书城“本地”范围；是否加入书架由用户决定，避免书架被扫描目录塞满。
3. OPDS 书籍默认只保留目录元数据，用户点击下载或加入书架时再按源能力拉取文件。
4. 本地和 OPDS 若识别为同一本书，不自动强行合并；可以提供“关联版本”能力，但保留来源可见性。
5. 阅读进度按 `bookId + renditionId` 保存；不同格式/版本的同名书不共享进度，除非用户主动合并。
