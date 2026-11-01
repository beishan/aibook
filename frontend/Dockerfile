# 前端 Dockerfile
# 使用多阶段构建

# 第一阶段：构建
FROM node:24-alpine AS builder

ARG NPM_REGISTRY=https://registry.npmjs.org

WORKDIR /app

# 复制 package.json 并安装依赖（利用 Docker 缓存）
COPY package*.json ./
RUN --mount=type=cache,id=aibook-frontend-npm-cache,target=/root/.npm,sharing=locked \
    npm ci --registry="${NPM_REGISTRY}" \
    --prefer-offline --no-audit --no-fund \
    --fetch-retries=5 \
    --fetch-retry-mintimeout=10000 \
    --fetch-retry-maxtimeout=60000 \
    --fetch-timeout=300000 \
    --maxsockets=5

# 复制源代码并构建
COPY . .
RUN npm run build

# 第二阶段：运行（使用 Nginx）
FROM nginx:alpine

ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata \
    && cp "/usr/share/zoneinfo/${TZ}" /etc/localtime \
    && echo "${TZ}" > /etc/timezone

# 复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 构建环境的 umask 不应影响 Nginx 读取公开静态资源
RUN find /usr/share/nginx/html -type d -exec chmod 755 {} + \
    && find /usr/share/nginx/html -type f -exec chmod 644 {} +

# 复制 Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 暴露端口
EXPOSE 80

# 启动 Nginx
CMD ["nginx", "-g", "daemon off;"]
