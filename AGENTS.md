# 汗牛充栋 · 阅读书籍管理系统

请先阅读 requires_1.md 项目需求文件

## 项目概述

**汗牛充栋**是一个面向个人及家庭用户的私有化图书管理与阅读平台，部署于家庭局域网 NAS 设备，支持本地书籍管理、网络书籍爬取、多端在线阅读，以及主流开源客户端协议接入。

**核心目标**：构建一个"买书、存书、读书"一站式私有书库，数据完全自持，无依赖外部订阅服务。

## 技术栈

| 层次 | 技术选型 |
|---|---|
| 前端 | Vue 3 + Vite + Pinia + Vue Router + Element Plus |
| 后端 | Java 21 + Spring Boot 3 + Spring Security |
| 数据库 | PostgreSQL 16 |
| 搜索索引 | PostgreSQL 全文检索 |
| 缓存 | Caffeine（进程内） |
| 任务调度 | Spring Scheduler |
| 容器化 | Docker + Docker Compose |
| 文件存储 | Docker Volume（本地文件系统） |
| 代码托管 | GitHub |

## 项目结构

```
ai-book/
├── backend/                    # 后端 Spring Boot 项目
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   └── resources/
│   │   └── test/
│   └── pom.xml
├── frontend/                   # 前端 Vue 3 项目
│   ├── src/
│   │   ├── assets/
│   │   ├── components/
│   │   ├── views/
│   │   ├── stores/
│   │   ├── router/
│   │   └── utils/
│   ├── index.html
│   └── package.json
├── docker/                     # Docker 配置
│   ├── docker-compose.yml
│   └── nginx/
├── docs/                       # 项目文档
├── resources/                  # 资源文件（书籍、封面等）
└── AGENTS.md
```

## 常用命令

### Docker 部署
```bash
# 启动所有服务
docker-compose up -d

# 查看日志
docker-compose logs -f

# 重新构建并启动
docker-compose up -d --build

# 停止所有服务
docker-compose down
```

### 后端开发
```bash
cd backend

# 编译
mvn clean package

# 运行（开发环境）
mvn spring-boot:run

# 运行测试
mvn test
```

### 前端开发
```bash
cd frontend

# 安装依赖
npm install

# 开发服务器
npm run dev

# 构建生产版本
npm run build

# 类型检查
npm run build  # 包含 vue-tsc
```

## 数据库

使用 PostgreSQL 16，通过 Docker Compose 启动。

关键数据表：
- `books` - 书籍基本信息
- `book_files` - 书籍文件信息
- `users` - 用户信息
- `reading_progress` - 阅读进度
- `book_lists` - 书单

## 开发规范

### 代码风格
- **Java**: 遵循 Google Java Style Guide
- **Vue/TypeScript**: 使用 Composition API + `<script setup>`

### Android UI 规范
- 安卓端后续新增或调整按钮时，点击态不要使用阴影或按压投影效果；优先使用颜色、透明度、边框或轻量背景变化表达反馈。

### Tab 与组合选择按钮规范
- 后续新增或调整 Tab 时，默认使用带平滑移动选中指示器的 macOS 26 风格滑块分段控件，不再使用仅改变背景色的普通 Tab。
- 具有互斥选中状态的组合按钮同样使用滑块分段控件；执行独立即时动作、可多选或包含危险操作的按钮组不使用选中滑块，以免错误表达状态。
- 滑块控件应保持选项尺寸协调，并适配键盘切换、焦点样式、移动端横向滚动及 `prefers-reduced-motion`。

### 需求变更记录
- 开发需求统一记录在 `docs/requirement-change-log.md`，最新记录置于最上方。
- 开始开发时可登记为“进行中”；每次开发完成并完成必要验证后，必须在结束任务前新增或更新对应记录。
- 记录至少包含：需求编号、需求时间、完成时间、状态、需求内容、完成情况、主要改动和验证结果。
- 部分完成、取消或未执行验证时必须如实注明，不得记录为“已完成”。
- 仅提供方案且没有修改项目文件的咨询无需记录；方案进入实际开发后必须记录。

### 版本号管理
- 应用版本号遵循 SemVer，唯一来源为 `frontend/package.json` 的 `version`，`frontend/package-lock.json` 必须同步。
- 每个产生项目文件变更且完成的需求，结束前必须更新版本号；默认至少递增修订号，兼容性功能可由开发者决定是否递增次版本号。
- 主版本号不得自行递增；如认为需要升级主版本，必须先询问用户并获得确认。
- 需求变更记录应注明该需求完成后的应用版本号。

### 开源方案优先
- 后续功能开发前，应优先调研是否已有成熟、活跃且与当前技术栈匹配的开源项目或开源库，避免重复实现已有能力。
- 调研开源方案时，应评估功能匹配度、许可证、维护活跃度、安全性、资源占用、私有化部署适配性与集成成本。
- 只读调研可直接进行；如准备引入依赖、复制或改造开源代码、部署开源服务或对项目架构产生影响，必须先向用户说明候选项目、许可证、引入范围、主要收益与风险，获得确认后才能使用。
- 未找到合适方案或用户不同意引入时，再在现有技术栈内自主实现。

### Git 提交
- 使用中文提交信息
- 格式: `<类型>: <描述>`
- 类型: feat, fix, refactor, docs, style, test, chore

### 分支策略
- `main` - 生产分支
- `develop` - 开发分支
- `feature/*` - 功能分支
- `fix/*` - 修复分支

## 环境变量

关键环境变量在 `.env` 文件中配置：

```bash
# 数据库密码
DB_PASSWORD=memoryvault

# JWT 密钥（生产环境必须修改）
JWT_SECRET=your-secret-key
```

## 硬件要求

- **开发环境**: macOS/Linux，8GB+ RAM
- **生产环境**: 飞牛 NAS，Docker 部署
