# ============================================================
# 绪见 · 根目录 Dockerfile
# 作用：让 Railway 直接用 Docker 构建，绕开 Railpack 在仓库根目录
#       探测不到 Java（后端在 see_you/ 子目录、根目录无 pom.xml）的问题。
# 本地不使用，仅用于云端部署；不影响本地 mvn / IDEA 运行方式。
# ============================================================

# ---------- 阶段一：Maven 编译打包，产出 fat jar ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先复制 pom.xml，命中 Docker 层缓存（依赖不变时不必重新下载）
COPY see_you/pom.xml ./pom.xml
# 复制后端源码
COPY see_you/src ./src
# 跳过测试，产出可执行 jar
RUN mvn -B clean package -DskipTests

# ---------- 阶段二：运行阶段，只带 JRE，镜像更小 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# 从构建阶段拷出 fat jar（产物名见 pom：see_you-0.0.1-SNAPSHOT.jar）
COPY --from=build /build/target/see_you-0.0.1-SNAPSHOT.jar app.jar

# 时区；Railway 运行时会把 $PORT、数据库连接等注入为环境变量
ENV TZ=Asia/Shanghai
EXPOSE 10000

# 容器启动即运行 Spring Boot
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
