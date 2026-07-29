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
| `aibook-frontend` | Web 前端与 API 反向代理 | `8291` |
| `aibook-backend` | Spring Boot API | `8292` |
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

在飞牛 NAS 上创建主书籍目录，例如：

```text
/vol1/docker/aibook/books
```

不同飞牛 NAS 的存储路径可能不同，应以文件管理器显示的真实绝对路径为准。
`BOOKS_PATH` 是 Docker 宿主机路径，不是 Jenkins 容器内路径。主目录固定映射
到容器内的 `/scanfolder`。

后端以非 root 用户运行。为避免依赖容器 UID，部署使用书库目录的宿主机用户组
GID 授权。先查询目录的数字 GID：

```bash
stat -c '%g' /vol1/docker/aibook/books
```

将输出填写到生产环境文件的 `BOOKS_GID`，然后授予该组递归读取和目录进入
权限，并让以后新增的内容继承权限：

```bash
sudo setfacl -R -m g:BOOKS_GID:rX,m::rX /vol1/docker/aibook/books
sudo setfacl -m d:g:BOOKS_GID:rX,d:m::rX /vol1/docker/aibook/books
```

把命令中的 `BOOKS_GID` 替换为实际数字，例如 `1001`。如果应用需要修改书库
原文件，可将 `rX` 改成 `rwX`；仅扫描和阅读时建议保持只读权限。新增书籍目录
时，优先放在 `BOOKS_PATH` 下，以自动继承默认 ACL。

如果书籍分布在不同宿主机目录或存储卷，可以通过 Jenkins 的
`BOOKS_MOUNTS` 多行参数增加任意数量的附加挂载：

```text
/vol1/1000/novels:/scanfolder/novels:ro
/vol1/1000/history:/scanfolder/history:ro
/vol2/1000/comics:/scanfolder/comics:ro
```

每行格式为：

```text
宿主机绝对路径:容器绝对路径[:ro|rw]
```

- 容器路径必须是 `/scanfolder/` 下的独立子目录，不能重复。
- 未填写模式时默认为 `ro`；仅扫描和阅读时建议保持只读。
- 部署脚本禁止根目录、相对路径和包含 `..` 的路径。
- 使用长格式 bind mount，宿主机目录不存在时部署会失败，不会静默创建空目录。

附加目录与主目录 GID 不同时，把所需数字 GID 使用逗号填写到
`BOOKS_GIDS`，例如：

```text
1001,1002,1003
```

每个附加目录仍需在 NAS 上配置对应的 ACL。部署后可在“设置 → 扫描目录”
中添加 `/scanfolder/novels`、`/scanfolder/history` 等容器路径。

### 可选字体目录

字体既可以在网页中上传，也可以从 NAS 目录扫描。若需要扫描 NAS 字体，设置
`FONTS_PATH` 和 `FONTS_GID`，主字体目录会只读映射到 `/fontfolder`：

```text
FONTS_PATH=/vol1/docker/aibook/fonts
FONTS_GID=1001
```

字体目录不需要配置时，将两个参数同时留空即可，部署和网页上传字体功能不受
影响。部署脚本只在配置主字体目录后合并
`docker/docker-compose.fonts.yml`，因此基础 Compose 配置不依赖任何字体变量。
字体文件只需读取权限，建议在 NAS 上配置组 ACL：

```bash
sudo setfacl -R -m g:FONTS_GID:rX,m::rX /vol1/docker/aibook/fonts
sudo setfacl -m d:g:FONTS_GID:rX,d:m::rX /vol1/docker/aibook/fonts
```

多个字体目录通过 `FONT_MOUNTS` 和 `FONT_GIDS` 配置：

```text
/vol1/1000/fonts-cn:/fontfolder/chinese:ro
/vol2/1000/fonts-en:/fontfolder/latin:ro
```

- 容器路径必须位于 `/fontfolder/` 下且不能重复。
- 字体挂载始终只读；省略模式时默认 `ro`，显式填写 `rw` 会被拒绝。
- 宿主机和容器路径必须是绝对路径，不能是根目录或包含 `..` 路径段。
- `FONTS_GID`、`FONT_GIDS` 只接受有效数字 GID。
- 使用长格式 bind mount；宿主机目录不存在时部署失败，不会自动创建空目录。

附加字体目录所需的组 GID 使用逗号填写，例如
`FONT_GIDS=1001,1002`。部署后在“设置 → 字体管理”中配置 `/fontfolder` 或
`/fontfolder/chinese` 等容器内扫描路径。

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
2. 替换所有中文占位值，填写 NAS 的真实 `BOOKS_PATH` 和书库组的数字
   `BOOKS_GID`。附加目录也可以填写到 `BOOKS_MOUNTS` 和 `BOOKS_GIDS`；
   环境文件中多个挂载使用分号分隔。需要扫描 NAS 字体时再填写
   `FONTS_PATH`、`FONTS_GID`、`FONT_MOUNTS` 和 `FONT_GIDS`。
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
| `FRONTEND_PORT` | `8291` | Web 前端端口 |
| `BACKEND_PORT` | `8292` | 后端健康检查端口 |
| `BOOKS_PATH` | `/vol1/1000/books` | 主书库宿主机路径，映射到 `/scanfolder` |
| `BOOKS_GID` | `1001` | 主书库目录的数字 GID |
| `BOOKS_MOUNTS` | 空 | 附加书库多行挂载配置 |
| `BOOKS_GIDS` | 空 | 附加目录 GID，多个值用逗号分隔 |
| `FONTS_PATH` | 空 | 可选主字体目录，只读映射到 `/fontfolder` |
| `FONTS_GID` | 空 | 主字体目录的数字 GID；配置路径时必填 |
| `FONT_MOUNTS` | 空 | 可选附加字体目录多行挂载配置，始终只读 |
| `FONT_GIDS` | 空 | 附加字体目录 GID，多个值用逗号分隔 |
| `SKIP_TESTS` | `false` | 仅在紧急发布时跳过后端测试 |

非空 Jenkins 参数会覆盖 Secret file 中同名配置。`BOOKS_MOUNTS` 留空时会读取
Secret file 中的单行分号格式，便于固定配置；临时发版也可以直接在 Jenkins
多行输入框中调整。字体参数使用相同规则；`FONTS_PATH`、`FONTS_GID` 都留空
时不会生成字体挂载，也不会出现 Compose 缺少变量的错误。端口被其他应用占用
时，在执行构建时修改参数即可。

## 六、首次部署

首次运行 Pipeline 时没有旧应用和数据库，因此：

- 数据库备份阶段会提示跳过，这是正常现象。
- 自动回滚没有上一版本可用。
- Compose 会创建网络和持久化 Volume。
- Maven 和 Docker 首次下载依赖、基础镜像会耗时较长。

部署完成后访问：

```text
http://192.168.31.155:8291/
http://192.168.31.155:8292/actuator/health
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

### `BOOKS_PATH` 或 `BOOKS_MOUNTS` 挂载失败

确认配置的是 NAS 宿主机绝对路径。即使 Jenkins 容器中不存在该路径，只要
Docker 守护进程所在的 NAS 宿主机存在即可。

如果日志出现 `AccessDeniedException: /scanfolder`，确认后端已经加入配置的
附加组，并检查宿主机 ACL：

```bash
docker exec aibook-backend id
getfacl -p /vol1/docker/aibook/books
docker exec aibook-backend ls /scanfolder
docker inspect --format '{{range .Mounts}}{{println .Source "->" .Destination "RW=" .RW}}{{end}}' aibook-backend
```

ACL 中应存在与 `BOOKS_GID` 对应且有效权限为 `r-x` 的组条目。修改
`BOOKS_GID` 或 `BOOKS_GIDS` 后必须重新创建后端容器，单纯重启不会更新附加组。
如果部署脚本提示宿主机路径不存在，应检查路径拼写以及该路径是否真实存在于
Docker 守护进程所在的 NAS，而不是只存在于 Jenkins 容器内。

### `FONTS_PATH` 或 `FONT_MOUNTS` 挂载失败

字体目录与书库目录使用相同的组权限机制，但始终以只读方式挂载。先确认
`FONTS_PATH` 和 `FONTS_GID` 已同时配置，再检查容器内权限和实际挂载：

```bash
docker exec aibook-backend id
docker exec aibook-backend ls -la /fontfolder
docker inspect --format '{{range .Mounts}}{{if eq .Destination "/fontfolder"}}{{println .Source "->" .Destination "RW=" .RW}}{{end}}{{end}}' aibook-backend
```

预期 `RW=false`。修改 `FONTS_GID` 或 `FONT_GIDS` 后需要重新创建后端容器。

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
