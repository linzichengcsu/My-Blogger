# syntax=docker/dockerfile:1

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 先单独拷 pom 预热依赖缓存
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
# 测试使用 H2 内存库、不依赖外部中间件（已在 CI 中执行），镜像构建阶段跳过加速
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q clean package -DskipTests

# 解出运行目录：Spring Boot 4 已移除 layertools jarmode，改用 tools（瘦 jar + lib/ 布局）
WORKDIR /build/target
RUN java -Djarmode=tools -jar blog-0.0.1-SNAPSHOT.jar extract --destination /build/application

# ---------- 运行阶段 ----------
FROM eclipse-temurin:21-jre-jammy
# curl 供健康检查使用；建非 root 用户与 prod 配置中写死的两个目录
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -r -u 1001 appuser \
    && mkdir -p /app /var/www/uploads /var/log/blog \
    && chown -R appuser:appuser /app /var/www/uploads /var/log/blog

WORKDIR /app
# 瘦 jar + lib/ 依赖目录：代码变更只重建 jar 小层，lib 层走缓存
COPY --from=build --chown=appuser:appuser /build/application/ ./

USER appuser
EXPOSE 8080
# JVM 按容器内存限额自适应；JAVA_OPTS 可在 compose 中追加
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar blog-0.0.1-SNAPSHOT.jar"]
