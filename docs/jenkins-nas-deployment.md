# 飞牛 NAS Jenkins 部署指南

本文档用于将 `git@github.com:beishan/aibook.git` 的 `main` 分支部署到飞牛 NAS
上的 Docker。Jenkins 本身也运行在该 NAS 的 Docker 中，并且已经能够执行
`docker` 命令和通过 SSH 拉取 GitHub 仓库。

## 一、部署结构

Jenkins Pipeline 会完成以下工作：

1. 从 GitHub 检出 `main`。
2. 运行后端测试。
3. 构建带 Jenkins 构建号和 Git 提交号的前后端镜像。
4. 备份 PostgreSQL。
5. 使用 Docker Compose 更新服务。
6. 检查全部容器及局域网访问地址。
7. 部署失败时恢复上一版本前后端镜像。

生产容器如下：

| 容器 | 用途 | 默认对外端口 |
|---|---|---:|
| `aibook-frontend` | Web 前端与 API 反向代理 | `8091` |
| `aibook-backend` | Spring Boot API | `8092` |
| `aibook-postgres` | PostgreSQL 16 | 不对外暴露 |
| `aibook-redis` | Redis 7 | 不对外暴露 |
| `aibook-minio` | MinIO API / 控制台 | `9000` / `9001` |

## 二、Jenkins 前置条件

你现有的 Jenkins 已经成功部署过其他 Docker 项目，通常只需确认：

```bash
git --version
docker version
docker compose version
curl --version
```

如果 `docker compose version` 不可用，部署脚本也会尝试旧命令
`docker-compose`。

后端测试和正式构建都在 `maven:3.9-eclipse-temurin-21` 镜像内运行，因此
Jenkins 容器不需要额外安装 Maven 或 Java 21，也不会受 Jenkins 自身 Java
版本影响。

Jenkins 容器需要能够控制 NAS 的 Docker。常见做法是挂载：

```text
/var/run/docker.sock:/var/run/docker.sock
```

该挂载具有很高权限，只应允许可信 Jenkins 管理员使用。

## 三、创建 NAS 数据目录

在飞牛 NAS 上创建书籍目录，例如：

```text
/vol1/docker/aibook/books
```

不同飞牛 NAS 的存储路径可能不同，应以文件管理器显示的真实绝对路径为准。
`BOOKS_PATH` 是 Docker 宿主机路径，不是 Jenkins 容器内路径。

应用的 PostgreSQL、Redis、MinIO、上传文件、数据库备份和部署状态使用以下
Docker Volume：

```text
aibook-postgres-data
aibook-redis-data
aibook-minio-data
aibook-uploads-data
aibook-backups
aibook-deploy-state
```

更新应用或执行 Jenkins `cleanWs()` 不会删除这些 Volume。

## 四、创建生产环境凭据

1. 复制仓库中的 `docker/.env.production.example`。
2. 替换所有中文占位值，并填写 NAS 的真实 `BOOKS_PATH`。
3. 在 Jenkins 打开：
   `Manage Jenkins → Credentials → System → Global credentials`。
4. 新增 `Secret file` 类型凭据。
5. 上传填写完成的环境文件。
6. 将凭据 ID 设置为：

```text
aibook-production-env
```

建议使用随机值生成密码和 JWT 密钥，例如：

```bash
openssl rand -base64 48
```

生产环境文件不要提交到 Git。流水线只在运行阶段读取 Jenkins 提供的临时
凭据文件，任务结束后执行 `cleanWs()`。

## 五、创建 Jenkins Pipeline

新建 Pipeline 任务，可以选择以下任一方式。

### 推荐：Pipeline script from SCM

- SCM：Git
- Repository URL：`git@github.com:beishan/aibook.git`
- Branch Specifier：`*/main`
- Script Path：`Jenkinsfile`
- SSH Credentials：使用当前已经能够拉取 `beishan/dog.git` 的凭据

仓库中的 `Jenkinsfile` 自身也会检出：

```text
git@github.com:beishan/aibook.git
```

任务参数：

| 参数 | 默认值 | 说明 |
|---|---|---|
| `NAS_HOST` | `192.168.31.155` | Jenkins 用于访问部署结果的 NAS 地址 |
| `FRONTEND_PORT` | `8091` | Web 前端端口 |
| `BACKEND_PORT` | `8092` | 后端健康检查端口 |
| `SKIP_TESTS` | `false` | 仅在紧急发布时跳过后端测试 |

Jenkins 参数会覆盖 Secret file 中同名的端口配置。端口被其他应用占用时，
在执行构建时修改参数即可。

## 六、首次部署

首次运行 Pipeline 时没有旧应用和数据库，因此：

- 数据库备份阶段会提示跳过，这是正常现象。
- 自动回滚没有上一版本可用。
- Compose 会创建网络和持久化 Volume。
- Maven 和 Docker 首次下载依赖、基础镜像会耗时较长。

部署完成后访问：

```text
http://192.168.31.155:8091/
http://192.168.31.155:8092/actuator/health
http://192.168.31.155:9001/
```

Actuator 只公开健康检查端点，不公开环境变量或其他管理信息。

## 七、GitHub 自动触发

如果 GitHub 能访问 Jenkins：

1. 在 Jenkins 任务中启用 GitHub hook trigger。
2. 在 GitHub 仓库打开 `Settings → Webhooks`。
3. 添加 Jenkins Webhook 地址，通常为：

```text
https://你的Jenkins地址/github-webhook/
```

4. Content type 选择 `application/json`。
5. 只选择 Push events。

如果 Jenkins 只在家庭局域网可访问，GitHub 无法直接调用 Webhook。可以在
Jenkins 任务中使用 `Poll SCM`，例如每五分钟检查一次：

```text
H/5 * * * *
```

## 八、查看日志

在 Jenkins 工作区或手动检出的仓库内，准备好生产环境文件后执行：

```bash
./scripts/deploy.sh logs /path/to/aibook-production.env
```

也可以直接查看单个容器：

```bash
docker logs --tail=200 aibook-backend
docker logs --tail=200 aibook-frontend
```

所有服务都配置了 Docker 日志轮转，默认单文件 10 MB，保留 3 个文件。
Jenkins 部署成功后只清理 `aibook-backend` 和 `aibook-frontend` 仓库中的旧
版本镜像，默认各保留最近 5 个版本，不会执行影响其他项目的全局镜像清理。

## 九、手动回滚

自动部署会把上一版本镜像信息存入 `aibook-deploy-state` Volume。手动回滚：

```bash
./scripts/rollback.sh /path/to/aibook-production.env
```

回滚只替换前后端应用镜像，不会删除数据库、Redis、MinIO 或上传文件。

查看当前镜像：

```bash
docker inspect --format '{{.Config.Image}}' aibook-backend
docker inspect --format '{{.Config.Image}}' aibook-frontend
```

## 十、数据库备份与恢复

每次更新前会在 `aibook-backups` Volume 中创建 PostgreSQL custom-format 备份，
默认保留最近 10 份。

列出备份：

```bash
docker run --rm \
  -v aibook-backups:/backups \
  postgres:16-alpine \
  ls -lh /backups
```

恢复数据库属于高风险操作，应先停止后端并再次确认备份文件名。基本流程为：

```bash
docker stop aibook-backend
docker run --rm \
  --network aibook-network \
  -v aibook-backups:/backups \
  -e PGPASSWORD='数据库密码' \
  postgres:16-alpine \
  pg_restore --clean --if-exists \
    -h postgres -U aibook -d aibook \
    /backups/选定的备份文件.dump
docker start aibook-backend
```

数据库密码不要写入 Jenkins 日志。实际恢复前还应确认应用版本与数据库结构
兼容。

## 十一、常见问题

### `BOOKS_PATH` 挂载失败

确认配置的是 NAS 宿主机绝对路径。即使 Jenkins 容器中不存在该路径，只要
Docker 守护进程所在的 NAS 宿主机存在即可。

### 健康检查一直是 `starting`

查看：

```bash
docker inspect aibook-backend
docker logs --tail=200 aibook-backend
```

重点检查数据库密码、Redis 密码、MinIO 凭据和 NAS 目录权限。

### 前端正常但 API 不通

确认 `aibook-backend` 健康，并确认前后端都连接到 `aibook-network`。前端
Nginx 通过容器服务名 `backend:8080` 访问后端，不使用 NAS 的映射端口。

### 端口冲突

在 Jenkins 构建参数中修改 `FRONTEND_PORT` 或 `BACKEND_PORT`。MinIO 端口
则在 Secret file 中修改 `MINIO_API_PORT` 与 `MINIO_CONSOLE_PORT`。
