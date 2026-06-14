FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 先在仓库根目录执行 mvn package，再构建镜像，避免容器内重复下载 Maven 依赖。
COPY ai-orchestration-service/target/ai-orchestration-service-*.jar app.jar

EXPOSE 8002

ENTRYPOINT ["java", "-jar", "app.jar"]
