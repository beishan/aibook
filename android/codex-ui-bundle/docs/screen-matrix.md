# Screen Matrix

| Screen | Route | Main content |
|---|---|---|
| BookshelfGrid | `bookshelf/grid` | [{'type': 'ContinueReadingCard'}, {'type': 'FolderRow', 'title': '文件夹', 'actions': ['viewAll']}, {'type': 'BookGrid', 'title': '全部书籍', 'columns': 3, 'viewToggle': ['grid', 'list']}] |
| BookshelfList | `bookshelf/list` | [{'type': 'ContinueReadingCompact'}, {'type': 'FolderRow'}, {'type': 'BookList'}] |
| ShelfFolders | `bookshelf/folders` | {'type': 'FolderList', 'sort': 'name'} |
| ShelfFolderDetail | `bookshelf/folder/{folderId}` | {'type': 'BookGrid', 'columns': 3} |
| BookshelfBatchManage | `bookshelf/batch` | {'type': 'SelectableBookGrid'} |
| SortFilter | `bookshelf/sort-filter` | [{'title': '排序方式', 'type': 'RadioList', 'options': ['最近阅读', '最近加入', '书名 A-Z', '作者 A-Z', '阅读进度']}, {'title': '筛选', 'type': 'FilterList', 'items': ['来源', '格式', '是否下载', '是否已读', '是否收藏']}] |
| LocalBooks | `bookstore/local` | {'type': 'LocalBookCollection'} |
| LocalScan | `bookstore/local/scan` | {'progress': True, 'stats': ['扫描目录', '发现书籍', '扫描进度'], 'currentPath': True, 'currentFile': True} |
| ScanDirectories | `bookstore/local/scan-directories` | {'type': 'DirectoryList', 'supportsIncludeSubdirs': True} |
| ScanResult | `bookstore/local/scan-result` |  |
| ImportBooks | `bookstore/local/import` |  |
| OpdsServices | `bookstore/opds` | {'type': 'OpdsServiceList', 'card': 'OpdsServiceCard'} |
| OpdsServiceDetail | `bookstore/opds/{serviceId}` | ['最近加入', '最近更新'] |
| OpdsCategories | `bookstore/opds/{serviceId}/categories` | {'type': 'CategoryList', 'showCount': True} |
| OpdsCategoryBooks | `bookstore/opds/{serviceId}/category/{categoryId}` | {'type': 'BookGrid', 'columns': 3} |
| BackendLibraryHome | `bookstore/backend` | [{'title': '最近加入', 'type': 'HorizontalBooks', 'action': '查看更多'}, {'title': '收藏', 'type': 'HorizontalBooks', 'action': '查看更多'}, {'title': '书单', 'type': 'BooklistPreviewRows', 'limit': 2}] |
| BackendRecent | `bookstore/backend/recent` | {'type': 'BookCollection'} |
| BackendFavorites | `bookstore/backend/favorites` | {'type': 'BookList'} |
| Booklists | `bookstore/backend/booklists` | {'type': 'BooklistList'} |
| BooklistDetail | `bookstore/backend/booklist/{booklistId}` | {'type': 'BookGrid', 'columns': 2} |
| BooklistNew | `bookstore/backend/booklist/new` |  |
| BooklistEdit | `bookstore/backend/booklist/{booklistId}/edit` |  |
| Search | `search` | ['最近搜索', '热门搜索'] |
| SearchResults | `search?query={query}` |  |
| BookSources | `book/{bookId}/sources` |  |
| Downloads | `downloads` |  |
| DownloadDetail | `downloads/{taskId}` |  |
| RecentReading | `reading/recent` | {'type': 'ReadingHistoryList', 'shows': ['cover', 'title', 'chapter', 'progress', 'lastReadAt']} |
| ReadingSettings | `settings/reading` | ['翻页方式', '字体设置', '页面设置'] |
| ThemeSettings | `settings/theme` | ['主题模式', '主题色', '应用图标', '显示设置', '书架封面圆角'] |
| BackupRestore | `settings/backup` | ['备份', '恢复', '自动备份', '备份文件位置'] |
| About | `settings/about` |  |