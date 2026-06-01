# 前端 Dockerfile
# 使用多阶段构建

# 第一阶段：构建
FROM node:20-alpine AS builder

WORKDIR /app

# 复制 package.json 并安装依赖（利用 Docker 缓存）
COPY package*.json ./
RUN npm ci

# 复制源代码并构建
COPY . .
RUN npm run build

# 第二阶段：运行（使用 Nginx）
FROM nginx:alpine

# 复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制 Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 暴露端口
EXPOSE 80

# 启动 Nginx
CMD ["nginx", "-g", "daemon off;"]
