# ============================================================
# 绪见 · 根目录 Dockerfile（Railway 云端构建用，本地不使用）
#
# 三阶段构建：
#   阶段零 fe-builder：Node 构建前端 Vite 产物 dist
#   阶段一 build     ：Maven 打包后端 fat jar
#   阶段二 run       ：仅 JRE 运行，同时挂载前端 dist 供后端同源托管
# ============================================================

# ---------- 阶段零：前端构建（Vite 打包到 /web/dist） ----------
FROM node:20-alpine AS fe-builder
WORKDIR /web

# 先复制依赖清单，命中 Docker 层缓存（依赖不变时跳过 npm ci）
COPY frontend/package.json frontend/package-lock.json ./
# --legacy-peer-deps：echarts-wordcloud@2.1.0 的对等依赖声明要求 echarts 5.x，
# 与本项目 echarts 6.x 的 peer 声明冲突；npm ci 默认严格校验会直接 ERESOLVE 失败。
# 这里保留 npm ci 按 lock 确定性安装，仅放宽对等依赖校验，不改业务依赖版本。
RUN npm ci --no-audit --no-fund --legacy-peer-deps

# 复制前端源码并构建；生产注入 VITE_API_BASE=""，使前端以同源相对路径直连后端真实接口
COPY frontend/ ./
ENV VITE_API_BASE=""
RUN npm run build

# ---------- 阶段一：Maven 编译打包，产出 fat jar ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先复制 pom.xml 命中层缓存（依赖不变时不必重新下载）
COPY see_you/pom.xml ./pom.xml
# 复制后端源码
COPY see_you/src ./src
# 跳过测试，产出可执行 jar
RUN mvn -B clean package -DskipTests

# ---------- 阶段二：运行阶段，只带 JRE，镜像更小 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# 后端 fat jar（产物名见 pom：see_you-0.0.1-SNAPSHOT.jar）
COPY --from=build /build/target/see_you-0.0.1-SNAPSHOT.jar app.jar

# 前端构建产物：WebConfig 的 web.dist-path 默认指向 /app/web/dist，由后端同源托管
COPY --from=fe-builder /web/dist /app/web/dist

# 时区；Railway 运行时会把 $PORT、数据库连接等注入为环境变量
ENV TZ=Asia/Shanghai
EXPOSE 10000

# 容器启动即运行 Spring Boot
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
