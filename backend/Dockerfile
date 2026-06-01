# 后端 Dockerfile
# 使用多阶段构建

# 第一阶段：构建
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# 复制 pom.xml 并下载依赖（利用 Docker 缓存）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源代码并构建
COPY src ./src
RUN mvn clean package -DskipTests -B

# 第二阶段：运行
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 创建非 root 用户
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 复制构建好的 jar 包
COPY --from=builder /app/target/*.jar app.jar

# 创建上传目录
RUN mkdir -p /app/uploads && chown -R appuser:appgroup /app

USER appuser

# 暴露端口
EXPOSE 8080

# 设置 JVM 参数
ENV JAVA_OPTS="-Xms256m -Xmx512m"

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
