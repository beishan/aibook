# 小说网站书籍采集与爬虫管理功能需求文档

## 一、项目背景

当前书籍后台管理服务已经具备：

- TXT、EPUB 等格式书籍管理；
- 书籍元信息管理；
- 一本书多版本管理；
- 一本书多格式管理；
- TXT、EPUB 等格式转换能力；
- 书籍内容及文件存储；
- 书库管理。

现计划在现有系统基础上增加「小说网站采集/爬虫」能力。

系统需要能够针对不同小说网站配置独立的解析规则，从目标网站自动发现书籍、获取书籍元信息、章节目录及章节正文，并将采集到的内容作为独立的「采集书籍」进行管理。

采集书籍经过校验、整理和格式转换后，可以加入现有书库，并继续使用现有系统的多版本、多格式管理能力。

---

# 二、整体设计目标

本模块整体分为两个业务域：

## 2.1 采集中心

负责：

网站管理  
↓  
解析规则管理  
↓  
书籍发现  
↓  
书籍采集  
↓  
章节解析  
↓  
章节内容存储  
↓  
更新检测  
↓  
采集结果管理

这里保存的是：

> 「从互联网采集得到的原始书籍数据」

暂时不直接作为正式书库中的书籍。

---

## 2.2 正式书库

负责：

书籍管理  
↓  
版本管理  
↓  
格式管理  
↓  
TXT / EPUB 等文件管理  
↓  
书架、收藏等现有业务

采集完成后执行：

> 「加入书库」

才正式成为当前书籍管理系统中的 Book。

这样的好处是将：

**采集数据**

和

**正式书籍数据**

彻底解耦。

避免以后修改爬虫、重新采集、章节失败等问题污染正式书库。

---

# 三、推荐的数据存储方案

## 3.1 核心原则

不建议：

> 爬取一本书之后直接生成 TXT，然后只保存 TXT。

也不建议：

> 每采集一章直接写入 EPUB。

推荐采用：

> 「结构化章节数据作为主数据，TXT / EPUB 等文件作为生成产物」

即：

书籍元信息  
＋  
章节目录  
＋  
章节正文

保存到数据库/内容存储系统。

TXT、EPUB 只是根据这些结构化数据动态生成的书籍格式。

---

# 四、采集书籍数据模型

建议增加独立的数据模型。

例如：

## 4.1 crawler_site

爬虫网站配置。

主要字段：

```text
id

site_name
站点名称

site_code
站点唯一编码

base_url
网站根地址

home_url
网站首页

enabled
是否启用

auto_scan
是否自动扫描书籍

auto_crawl
是否自动采集发现的书籍

auto_update
是否自动检查章节更新

auto_import_library
采集完成是否自动加入书库

scan_interval
首页扫描周期

update_interval
书籍更新检测周期

request_interval
请求间隔

max_concurrency
最大并发数

timeout
请求超时时间

retry_count
失败重试次数

user_agent

cookie

headers

proxy

status

create_time

update_time
```

---

# 五、网站解析规则

每一个网站都有自己的页面结构，因此每个网站必须维护独立解析规则。

建议：

crawler_site  
↓  
crawler_site_rule

一个网站对应一套或多套解析规则。

例如：

```text
crawler_site_rule

id
site_id

rule_version

book_discovery_rule
书籍发现规则

book_detail_rule
书籍详情页规则

chapter_list_rule
章节目录规则

chapter_content_rule
章节正文规则

pagination_rule
分页规则

next_page_rule

encoding

content_clean_rule

enabled

create_time
update_time
```

---

# 六、规则应该解析哪些内容

## 6.1 首页/列表页规则

用于自动发现书籍。

需要能够解析：

- 书籍链接；
- 书籍 ID；
- 书籍名称；
- 作者；
- 封面；
- 分类；
- 更新时间；
- 最新章节；
- 下一页地址。

最重要的是：

```text
bookUrl
```

例如：

```text
/book/237808/
```

解析后转换成：

```text
https://目标网站/book/237808/
```

---

# 七、书籍详情解析规则

用于解析书籍元信息。

至少包括：

```text
书名

作者

封面

简介

分类

标签

连载状态

更新时间

最新章节

书籍原始 URL

网站 Book ID
```

同时建议保留：

```text
rawHtml
```

用于以后排查解析错误。

rawHtml 可以：

- 数据库存储压缩内容；
- 文件系统存储；
- 对象存储。

不建议长期直接放普通 VARCHAR/TEXT 数据库字段。

---

# 八、章节目录解析规则

章节列表页面需要解析：

```text
chapterName

chapterUrl

chapterIndex

chapterExternalId

vip

更新时间（如果网站提供）

章节状态
```

例如：

```text
第1章 xxxx
/book/237808/2636576.html
```

最终形成：

```text
章节1
章节2
章节3
……
章节N
```

---

# 九、章节正文解析

章节正文页需要提取：

```text
章节标题

正文内容

上一章 URL

下一章 URL
```

正文处理后保存：

```text
纯文本正文
```

而不是 HTML。

但可以同时选择性保存：

```text
originalHtml
```

用于以后重新清洗。

---

# 十、正文清洗规则

每个网站需要拥有独立的正文清洗配置。

支持：

删除：

- 广告；
- 网站名称；
- APP 下载提示；
- 推荐文字；
- 页脚；
- 上一章；
- 下一章；
- 返回目录；
- 作者推广文字；
- 网站固定水印；
- 空白行；
- 特定 CSS DOM 节点。

支持：

```text
CSS Selector 删除

XPath 删除

正则替换

字符串替换
```

例如：

```text
www.xxx.com
```

替换为空。

---

# 十一、采集书籍模型

增加：

```text
crawler_book
```

表示：

> 从某一个网站发现或者采集到的一本书。

主要字段：

```text
id

site_id

external_book_id

book_url

book_name

author

cover_url

cover_local_path

description

category

tags

book_status

latest_chapter

latest_chapter_url

source_update_time

discover_time

last_crawl_time

last_update_check_time

chapter_count

crawled_chapter_count

failed_chapter_count

crawl_status

import_status

library_book_id

create_time

update_time
```

---

# 十二、章节数据模型

增加：

```text
crawler_chapter
```

字段：

```text
id

crawler_book_id

external_chapter_id

chapter_index

chapter_name

chapter_url

content

content_hash

word_count

crawl_status

retry_count

source_update_time

crawl_time

error_message

create_time

update_time
```

这里的：

```text
content_hash
```

非常重要。

例如：

```text
SHA256(content)
```

用于判断：

> 网站上的某章节是否发生了修改。

---

# 十三、为什么结构化章节存储比直接 TXT 更合适

推荐结构：

```text
CrawlerBook
    ↓
CrawlerChapter
    ↓
Chapter Content
```

而不是：

```text
CrawlerBook
    ↓
book.txt
```

主要原因：

### 1. 支持增量更新

连载小说增加：

```text
第101章
```

只需要增加一条章节。

不用重新下载整本书。

---

### 2. 支持章节修改

如果：

```text
第55章
```

网站内容修改。

可以通过：

```text
content_hash
```

发现变化。

---

### 3. EPUB 很容易生成

EPUB 本身也是章节结构。

直接：

```text
crawler_chapter
→ XHTML
→ EPUB
```

即可。

---

### 4. TXT 很容易生成

按照：

```text
chapter_index
```

排序。

生成：

```text
书名

作者

简介

第一章

正文……

第二章

正文……
```

---

### 5. 后面可以扩展更多格式

例如：

```text
TXT
EPUB
MOBI
AZW3
Markdown
HTML
PDF
```

而不需要重新采集。

---

# 十四、爬虫页面

系统增加一个独立一级页面：

# 书籍爬虫

建议左侧菜单：

```text
书籍爬虫

├── 采集概览
├── 采集网站
├── 发现书籍
├── 采集书籍
├── 采集任务
├── 失败任务
└── 系统配置
```

---

# 十五、采集概览

Dashboard 展示：

```text
网站数量

启用网站

发现书籍数量

已采集书籍数量

采集中书籍数量

采集失败书籍数量

今日新增书籍

今日新增章节

待加入书库数量

已经加入书库数量
```

同时展示：

最近任务

```text
网站
书名
任务类型
章节数量
成功数量
失败数量
进度
耗时
状态
```

---

# 十六、采集网站页面

列表展示：

| 网站 | 地址 | 状态 | 书籍数量 | 自动扫描 | 自动采集 | 自动更新 | 自动入库 |
|---|---|---|---:|---|---|---|---|

支持：

```text
新增
编辑
删除
启用
禁用
手动扫描
测试解析规则
```

---

# 十七、新增网站

新增网站基本信息：

```text
网站名称

网站编码

网站首页

网站根地址

字符编码

请求 User-Agent

Cookie

Header

代理

访问间隔

最大并发

超时时间

失败重试
```

自动化配置：

```text
☑ 自动扫描网站

扫描周期：
每 6 小时

☑ 自动采集新书

☑ 自动检查章节更新

更新检查周期：
每 30 分钟

☐ 自动加入书库
```

---

# 十八、规则配置页面

建议提供：

## 基础规则

```text
书籍列表页
书籍详情页
章节目录页
章节内容页
```

---

## 解析规则

例如：

```text
书名 Selector

作者 Selector

封面 Selector

简介 Selector

状态 Selector

目录链接 Selector
```

章节规则：

```text
章节列表 Selector

章节名称 Selector

章节 URL Selector
```

正文规则：

```text
标题 Selector

正文 Selector

删除 Selector

正文清洗规则
```

---

# 十九、支持两种规则模式

建议系统同时支持：

## 模式一：配置式规则

例如：

```text
CSS Selector
XPath
Regex
JSONPath
```

适合简单网站。

无需修改代码。

---

## 模式二：代码解析器

复杂网站支持：

```java
BookCrawlerParser
```

接口。

例如：

```java
public interface BookCrawlerParser {

    List<CrawlerBookInfo> parseBookList(Page page);

    CrawlerBookInfo parseBookDetail(Page page);

    List<CrawlerChapterInfo> parseChapterList(Page page);

    CrawlerChapterContent parseChapter(Page page);
}
```

然后：

```java
ChunXiaoGeCrawlerParser
```

实现：

```java
BookCrawlerParser
```

---

# 二十、推荐采用「规则 + 插件」混合模式

这是整个设计里比较重要的一点。

不要完全写死 Java。

也不要全部依赖 CSS Selector。

推荐：

```text
简单网站
    ↓
配置解析

复杂网站
    ↓
自定义 Java Parser
```

网站配置增加：

```text
parserType

CONFIG
CUSTOM
```

CUSTOM 时：

```text
parserBean
```

例如：

```text
chunXiaoGeCrawlerParser
```

Spring 根据 Bean Name 获取解析器。

---

# 二十一、发现书籍页面

网站扫描并不等于立即爬取。

首页扫描后进入：

```text
发现书籍
```

展示：

```text
封面

书名

作者

网站

状态

最新章节

网站更新时间

发现时间

采集状态
```

操作：

```text
开始采集

查看网站

忽略

加入黑名单
```

批量操作：

```text
批量采集

批量忽略
```

---

# 二十二、采集指定书籍

除了自动扫描，必须支持：

# URL 手动采集

用户输入：

```text
https://xxx.com/book/237808/
```

系统：

```text
识别网站
      ↓
匹配 crawler_site
      ↓
读取该网站解析器
      ↓
解析书籍
      ↓
获取目录
      ↓
创建采集任务
```

---

# 二十三、手动添加书籍

另外支持：

```text
网站
+
网站 Book ID
```

例如：

```text
237808
```

系统根据 URL 模板：

```text
/book/{bookId}/
```

自动生成地址。

---

# 二十四、采集任务

建议把所有采集操作做成异步任务。

增加：

```text
crawler_task
```

类型：

```text
SITE_SCAN

BOOK_METADATA

BOOK_CHAPTER_LIST

BOOK_CONTENT

BOOK_UPDATE_CHECK

BOOK_FULL_CRAWL

BOOK_EXPORT

BOOK_IMPORT
```

---

# 二十五、任务状态

统一：

```text
WAITING

RUNNING

SUCCESS

PARTIAL_SUCCESS

FAILED

CANCELLED
```

---

# 二十六、书籍采集流程

完整流程：

```text
输入书籍 URL
     ↓
识别网站
     ↓
解析书籍详情
     ↓
创建 CrawlerBook
     ↓
获取章节目录
     ↓
创建 CrawlerChapter
     ↓
按照章节顺序建立任务
     ↓
采集章节正文
     ↓
清洗正文
     ↓
保存章节
     ↓
计算 Hash
     ↓
统计采集结果
     ↓
采集完成
```

---

# 二十七、采集进度

展示：

```text
总章节：1000

已完成：725

失败：3

等待：272

进度：72.5%
```

同时显示：

```text
当前章节

平均请求耗时

采集速度

开始时间

预计剩余任务数量
```

不强制展示 ETA，因为网络访问速度变化可能较大。

---

# 二十八、失败处理

失败章节必须单独记录。

例如：

```text
章节 225

URL

HTTP 状态码

失败原因

重试次数

最后失败时间
```

支持：

```text
重新采集

批量重试失败章节

忽略章节
```

---

# 二十九、断点续传

这是必须支持的能力。

如果一本：

```text
3000章
```

的小说采集到：

```text
1800章
```

服务重启。

恢复之后：

直接从未完成章节继续。

不能重新从第 1 章开始。

---

# 三十、自动更新

对于：

```text
连载中
```

书籍。

系统按照：

```text
update_interval
```

定期检查目录。

例如原来：

```text
100章
```

最新：

```text
105章
```

系统发现：

```text
新增5章
```

然后创建：

```text
5个章节采集任务
```

---

# 三十一、章节变化检测

不仅检查新增章节。

还应该检查：

```text
章节标题变化

章节 URL 变化

章节正文变化
```

正文变化可以通过：

```text
content_hash
```

判断。

---

# 三十二、采集书籍详情页

页面布局建议：

顶部：

```text
封面

书名

作者

网站

网站状态

采集状态

章节数量

采集数量

更新时间
```

按钮：

```text
继续采集

检查更新

重新扫描目录

重新采集失败章节

重新采集全部

生成格式

加入书库
```

---

# 三十三、章节列表

展示：

| # | 章节 | 状态 | 字数 | 更新时间 | 采集时间 |
|---|---|---|---:|---|---|

状态：

```text
未采集

等待采集

采集中

已完成

失败

内容异常
```

点击章节可以查看：

```text
原始网页

清洗后的正文

采集日志
```

---

# 三十四、书籍完整性检测

加入书库之前执行：

```text
章节是否全部完成

是否存在失败章节

是否存在空章节

是否存在重复章节

章节顺序是否异常

章节内容是否过短
```

结果：

```text
完整

基本完整

存在缺失

存在异常
```

---

# 三十五、格式转换

采集书籍页面支持：

```text
生成 TXT

生成 EPUB
```

后续扩展：

```text
MOBI

AZW3

PDF
```

转换使用现有格式转换模块。

但数据来源由：

```text
上传文件
```

扩展为：

```text
CrawlerBook + CrawlerChapter
```

---

# 三十六、TXT 生成规则

默认：

```text
《书名》

作者：xxx

简介：
xxxxxxxx


第一章 xxxxxx

正文……


第二章 xxxxxx

正文……
```

提供配置：

```text
章节标题格式

章节间空行数量

编码

换行符

是否包含简介
```

---

# 三十七、EPUB 生成

建议结构：

```text
metadata
cover
toc
chapter-0001.xhtml
chapter-0002.xhtml
chapter-0003.xhtml
...
```

Metadata：

```text
title

author

description

cover

category
```

自动生成：

```text
目录 TOC
```

---

# 三十八、生成文件不要直接加入正式书库

生成 TXT / EPUB 后：

CrawlerBook

可以拥有：

```text
CrawlerBookExport
```

例如：

```text
TXT
EPUB
```

但仍属于：

```text
采集中心
```

用户点击：

```text
加入书库
```

之后才进入正式 Book。

---

# 三十九、加入书库

支持：

```text
手动加入

自动加入
```

---

# 四十、手动加入

按钮：

```text
加入书库
```

弹窗：

```text
书名

作者

封面

简介

分类

标签

目标版本

需要生成格式
```

例如：

```text
☑ TXT
☑ EPUB
```

确认：

```text
CrawlerBook
      ↓
生成 Book
      ↓
生成 BookVersion
      ↓
生成 BookFormat
```

---

# 四十一、自动加入

网站配置：

```text
自动加入书库
```

开启后：

采集成功  
↓  
完整性检测通过  
↓  
生成指定格式  
↓  
自动创建正式 Book

可以配置：

```text
默认 TXT

默认 EPUB

TXT + EPUB
```

---

# 四十二、与现有多版本体系结合

假设书库已经存在：

```text
《XXX》
```

再次从网站获取该书。

系统应该尝试匹配：

```text
书名
+
作者
```

如果找到：

提示：

```text
发现书库中可能已经存在该书。
```

可以：

```text
作为新书

作为已有书的新版本
```

例如：

```text
《XXX》

版本：
春晓阁版本
```

这样可以充分利用现有：

```text
一本书
   ↓
多个版本
   ↓
多个格式
```

体系。

---

# 四十三、来源信息

正式书籍版本建议增加：

```text
sourceType
```

例如：

```text
UPLOAD

LOCAL

CRAWLER

OPDS
```

爬虫版本：

```text
sourceType = CRAWLER

sourceId = crawlerBookId

sourceSite = chunxiaoge

sourceUrl = xxx
```

这样以后可以：

```text
检查源站更新
```

---

# 四十四、连续更新正式书籍

如果：

CrawlerBook

已经加入书库。

以后发现：

```text
新增章节
```

可以配置：

```text
仅更新 CrawlerBook

或者

自动同步正式书库版本
```

建议默认：

```text
仅更新 CrawlerBook
```

然后提供按钮：

```text
同步到书库
```

避免正式版本被意外修改。

---

# 四十五、请求控制

必须支持：

```text
访问间隔

最大并发

随机延迟

请求超时

失败重试

HTTP 429 处理

HTTP 403 处理
```

网站维度单独控制。

例如：

```text
请求间隔：

1000-3000ms
```

避免短时间大量请求。

---

# 四十六、域名级限速

推荐：

```text
siteId
```

或者：

```text
domain
```

作为限流 Key。

例如：

```text
crawler:rate:chunxiaoge.com
```

可以使用：

```text
Redisson RRateLimiter
```

实现。

---

# 四十七、任务并发模型

推荐：

```text
书籍任务
     ↓
章节任务队列
     ↓
Crawler Worker
```

例如：

```text
BookCrawlerTask

ChapterCrawlerTask
```

不要：

一个线程：

```text
for 3000章
    请求章节
```

否则：

- 无法断点；
- 不方便控制重试；
- 不容易限速；
- 单任务持续时间过长。

---

# 四十八、建议架构

整体：

```text
Crawler Controller
        ↓
Crawler Service
        ↓
Crawler Task Service
        ↓
Crawler Scheduler
        ↓
Crawler Worker
        ↓
HTTP Client
        ↓
Parser
        ↓
Content Cleaner
        ↓
Crawler Repository
```

---

# 四十九、解析器架构

```text
BookCrawlerParser
          ↑
          │
 ┌────────┼────────┐
 │        │        │
SiteA    SiteB    SiteC
Parser   Parser   Parser
```

比如：

```text
ChunXiaoGeCrawlerParser
```

---

# 五十、HTTP 请求模块

建议独立：

```text
CrawlerHttpClient
```

负责：

```text
User-Agent

Cookie

Header

Proxy

Timeout

Retry

Rate Limit

Redirect

Encoding
```

Parser 只负责：

```text
HTML → Data
```

不要让 Parser 自己发送请求。

---

# 五十一、内容清洗模块

独立：

```text
BookContentCleaner
```

流程：

```text
HTML
 ↓
删除 DOM
 ↓
提取正文
 ↓
正则清洗
 ↓
HTML Entity 解码
 ↓
换行规范化
 ↓
去除异常空白
 ↓
Plain Text
```

---

# 五十二、任务日志

每次任务记录：

```text
taskId

site

book

chapter

url

HTTP Status

duration

retryCount

result

error
```

方便排查：

```text
网站规则变了

网站无法访问

章节不存在

解析规则失效
```

---

# 五十三、规则健康检测

这是后期很有用的功能。

每天抽样：

```text
1个书籍详情页

1个目录页

1个章节页
```

验证：

```text
书名是否解析成功

章节数量是否 > 0

正文长度是否 > 最小值
```

如果失败：

网站显示：

```text
解析规则异常
```

---

# 五十四、规则测试功能

编辑网站规则时提供：

```text
测试 URL
```

例如输入：

```text
https://xxx.com/book/123/
```

点击：

```text
测试
```

立即显示：

```text
书名：
作者：
简介：
封面：
状态：
目录地址：
```

章节测试显示：

```text
章节名称：
正文长度：
正文预览：
```

这样新增网站时不需要不断部署代码。

---

# 五十五、书籍去重

发现书籍时首先使用：

```text
site_id + external_book_id
```

作为唯一标识。

建议数据库唯一索引：

```text
UNIQUE(site_id, external_book_id)
```

如果没有 externalBookId：

使用：

```text
site_id + canonical_book_url
```

---

# 五十六、章节去重

建议：

```text
UNIQUE(crawler_book_id, external_chapter_id)
```

如果网站无章节 ID：

```text
crawler_book_id + chapter_url
```

---

# 五十七、网站规则版本

建议：

```text
rule_version
```

例如：

```text
1
2
3
```

因为网站改版以后：

```text
旧规则失效
```

可以发布：

```text
Version 2
```

保留 Version 1 的历史配置。

---

# 五十八、采集优先级

任务支持：

```text
LOW

NORMAL

HIGH
```

手动采集：

```text
HIGH
```

自动扫描：

```text
LOW
```

自动更新：

```text
NORMAL
```

避免后台批量扫描阻塞人工任务。

---

# 五十九、暂停和恢复

网站级：

```text
暂停全部任务
```

书籍级：

```text
暂停采集
继续采集
```

任务级：

```text
取消
重新执行
```

---

# 六十、定时任务

支持：

```text
网站扫描任务
```

例如：

```text
每天 02:00
```

支持：

```text
书籍更新任务
```

例如：

```text
每 30 分钟
```

不同网站可以设置不同周期。

---

# 六十一、采集策略

网站可以配置：

```text
DISCOVER_ONLY
只发现

AUTO_CRAWL
自动采集

AUTO_IMPORT
自动入库
```

实际上对应三个自动化级别：

### Level 1

```text
发现书籍
```

人工点击：

```text
采集
```

---

### Level 2

```text
发现
↓
自动采集
```

人工：

```text
加入书库
```

---

### Level 3

```text
发现
↓
自动采集
↓
自动检查完整性
↓
自动生成格式
↓
自动加入书库
```

---

# 六十二、建议默认策略

新网站默认：

```text
自动发现：关闭

自动采集：关闭

自动更新：开启

自动入库：关闭
```

因为刚添加的网站解析规则可能不稳定。

验证一段时间后再打开自动采集。

---

# 六十三、网站书籍列表

进入某网站：

```text
春晓阁
```

展示：

```text
全部

已发现

等待采集

采集中

采集完成

采集失败

已入库
```

支持搜索：

```text
书名

作者

网站 Book ID
```

---

# 六十四、系统级全局配置

增加：

```text
最大爬虫线程

默认请求间隔

默认超时时间

默认失败重试

最大单书并发章节数

文件临时目录

HTML 是否保存

HTML 保存天数

日志保存天数
```

---

# 六十五、代理支持

预留：

```text
HTTP Proxy

HTTPS Proxy
```

网站配置可以选择：

```text
不使用代理

使用默认代理

指定代理
```

---

# 六十六、Cookie / 登录支持

某些网站后续可能需要：

```text
Cookie
```

因此网站配置预留：

```text
cookie
```

同时允许：

```text
自定义 Header
```

例如：

```text
Referer

Accept-Language
```

但系统只应在获得授权、符合网站条款和适用法律的范围内采集内容，不设计绕过登录、付费、验证码、访问控制或其他技术限制的能力。

---

# 六十七、VIP / 不可访问章节

章节状态增加：

```text
FREE

VIP

LOCKED

UNKNOWN
```

如果页面只能读取部分正文：

标记：

```text
CONTENT_INCOMPLETE
```

而不是当成完整章节。

系统不应尝试绕过网站的付费或访问控制。

---

# 六十八、异常内容检测

例如章节内容：

```text
10个字
```

明显异常。

可以配置：

```text
minChapterLength = 100
```

低于长度：

```text
CONTENT_SUSPECTED
```

进入人工检查。

---

# 六十九、书籍完成状态

CrawlerBook：

```text
DISCOVERED

WAITING

CRAWLING_METADATA

CRAWLING_CHAPTER_LIST

CRAWLING_CONTENT

PARTIAL_SUCCESS

COMPLETED

FAILED

UPDATING

PAUSED
```

---

# 七十、书籍页面状态展示

例如：

```text
《XXX》

采集状态：
已完成

章节：
885 / 885

失败：
0

完整度：
100%

源站：
XXXX

最后检查：
2026-09-05 18:20

书库状态：
未加入
```

---

# 七十一、第一期建议范围

一期建议优先完成核心闭环。

## P0

必须有：

```text
网站管理

网站解析器

手动 URL 采集

书籍详情采集

章节目录采集

章节正文采集

结构化存储

采集进度

失败重试

断点续采

TXT 生成

EPUB 生成

手动加入书库
```

---

## P1

第二阶段：

```text
网站首页自动扫描

自动采集

定时更新

章节增量更新

自动入库

批量任务

规则配置 UI

采集 Dashboard
```

---

## P2

第三阶段：

```text
纯配置式爬虫

规则在线测试

规则版本

规则健康检测

代理池

高级内容清洗

多节点 Crawler Worker
```

---

# 七十二、最终系统关系

整个系统最终形成：

```text
                    小说网站
                       │
                       ↓
               ┌──────────────┐
               │ Crawler Site │
               └──────────────┘
                       │
                       ↓
              网站解析器 / Rules
                       │
                       ↓
                 发现 Book
                       │
                       ↓
                CrawlerBook
                       │
                       ↓
               Chapter List
                       │
                       ↓
              CrawlerChapter
                       │
                       ↓
                 Content
                       │
               ┌───────┴───────┐
               ↓               ↓
              TXT             EPUB
               │               │
               └───────┬───────┘
                       ↓
                  加入书库
                       ↓
                     Book
                       ↓
                  BookVersion
                       ↓
                  BookFormat
```

---

# 七十三、核心设计原则

整个功能建议遵守六条核心原则：

### 1. 爬虫数据和正式书库数据分离

```text
CrawlerBook != Book
```

这是最重要的一点。

### 2. 章节结构化数据是主数据

```text
CrawlerChapter
```

才是采集结果。

TXT / EPUB 是生成结果。

### 3. 每个网站都有独立 Parser

```text
Site → Parser
```

避免大量：

```java
if (site == xxx)
```

散落在业务代码里。

### 4. Parser 和 HTTP Client 分离

Parser 只负责：

```text
HTML → Data
```

网络请求由统一模块负责。

### 5. 所有采集行为任务化

不要同步 HTTP 请求直接爬整本书。

采用：

```text
Task + Worker
```

体系。

### 6. 正式书籍通过「发布/加入书库」产生

形成：

```text
互联网内容
↓
采集
↓
清洗
↓
校验
↓
格式生成
↓
发布
↓
正式书库
```

这样以后无论增加多少网站、多少格式，现有书库模型都基本不需要被爬虫逻辑侵入。