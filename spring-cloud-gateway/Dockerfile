FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 先在仓库根目录执行 mvn package，再构建镜像，避免容器内重复下载 Maven 依赖。
COPY spring-cloud-gateway/target/spring-cloud-gateway-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
