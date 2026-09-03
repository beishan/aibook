# Android Codex UI Bundle 逐页验收表

> 验收口径：页面路由存在不等于视觉完成。只有结构、Design Token、主要状态、交互和真机截图均核对后，才标记为“通过”。

| # | 页面 | 路由/入口 | 当前状态 |
|---:|---|---|---|
| 1 | 书架-卡片模式 | `bookshelf/grid` | 第二轮真机通过 |
| 2 | 书架-列表模式 | `bookshelf/list` | 待真机复核 |
| 3 | 书架文件夹列表 | `bookshelf/folders` | 第二轮真机通过 |
| 4 | 文件夹详情 | `bookshelf/folder/{folderId}` | 待真机复核 |
| 5 | 新建文件夹 | `bookshelf/folder/new` | 第二轮真机通过 |
| 6 | 批量管理 | `bookshelf/batch` | 待真机复核 |
| 7 | 排序筛选 | `bookshelf/sort-filter` | 第二轮真机通过 |
| 8 | 书籍详情-本地 | `book/{bookId}` | 待真机复核 |
| 9 | 书籍详情-OPDS | OPDS 书籍详情 | 待真机复核 |
| 10 | 书籍详情-后端 | 后端书籍详情 | 待真机复核 |
| 11 | 扫描本地书籍 | `bookstore/local/scan` | 第二轮已重做，待真机复核 |
| 12 | 扫描目录管理 | `bookstore/local/scan-directories` | 第二轮真机通过 |
| 13 | 扫描结果 | `bookstore/local/scan-result` | 第二轮已重做，待真机复核 |
| 14 | 导入书籍 | `bookstore/local/import` | 第二轮真机通过（空状态） |
| 15 | 本地书籍列表 | `bookstore/local` | 第二轮真机通过 |
| 16 | OPDS 服务列表 | `bookstore/opds` | 第二轮真机通过（空状态） |
| 17 | OPDS 服务详情 | `bookstore/opds/{serviceId}` | 第二轮已重做，待真机复核 |
| 18 | OPDS 分类 | `bookstore/opds/{serviceId}/categories` | 第二轮已重做，待真机复核 |
| 19 | OPDS 分类书籍 | `bookstore/opds/{serviceId}/category/{categoryId}` | 第二轮已重做，待真机复核 |
| 20 | 添加 OPDS 服务 | `bookstore/opds/add` | 第二轮真机通过 |
| 21 | 后端服务首页 | `bookstore/backend` | 空状态真机通过，待有数据状态 |
| 22 | 最近加入列表 | `bookstore/backend/recent` | 第二轮已重做，待真机复核 |
| 23 | 收藏列表 | `bookstore/backend/favorites` | 第二轮已重做，待真机复核 |
| 24 | 书单列表 | `bookstore/backend/booklists` | 第二轮已重做，待真机复核 |
| 25 | 书单详情 | `bookstore/backend/booklist/{booklistId}` | 第二轮已重做，待真机复核 |
| 26 | 新建书单 | `bookstore/backend/booklist/new` | 第二轮已重做，待真机复核 |
| 27 | 编辑书单 | `bookstore/backend/booklist/{booklistId}/edit` | 第二轮已重做，待真机复核 |
| 28 | 搜索页 | `search` | 第二轮真机通过 |
| 29 | 搜索结果页 | `search?query={query}` | 已接入本地/OPDS/后端，待后端数据复验 |
| 30 | 多来源版本选择 | `book/{bookId}/sources` | 待真机复核 |
| 31 | 多来源版本选择-重制稿 | `book/{bookId}/sources` | 待真机复核 |
| 32 | 热门搜索-重制稿 | `search` | 第二轮真机通过 |
| 33 | 搜索页-重制稿 | `search` | 第二轮真机通过 |
| 34 | 下载管理 | `downloads` | 空状态真机通过，待下载中状态 |
| 35 | 下载详情 | `downloads/{taskId}` | 待真机复核 |
| 36 | 最近阅读 | `reading/recent` | 第二轮真机通过 |
| 37 | 阅读设置 | `settings/reading` | 第二轮真机通过 |
| 38 | 主题与外观 | `settings/theme` | 第二轮真机通过 |
| 39 | 数据备份与恢复 | `settings/backup` | 第二轮真机通过 |
| 40 | 关于 | `settings/about` | 第二轮真机通过 |

## 当前阻塞与原则

- 小米系统当前禁止 ADB 注入触摸/按键，因此不能靠脚本逐路由点击截图；可正常安装、启动、抓取当前页面截图。
- 后续每批页面完成后先编译和单测，再安装真机；能实际进入的页面按视觉稿逐张截图比对。
- 未完成真机视觉核对的页面不会标记为“通过”，需求记录保持“部分完成”。
