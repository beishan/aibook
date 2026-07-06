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
| 任务调度 | Spring Scheduler |
| 容器化 | Docker + Docker Compose |
| 对象存储 | MinIO |
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
REDIS_PASSWORD=memoryvault
MINIO_PASSWORD=memoryvault

# JWT 密钥（生产环境必须修改）
JWT_SECRET=your-secret-key
```

## 硬件要求

- **开发环境**: macOS/Linux，8GB+ RAM
- **生产环境**: 飞牛 NAS，Docker 部署
