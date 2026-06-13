# 多阶段构建：先编译 spring-cloud-gateway，再运行
FROM maven:3.9.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# 1. 复制所有 pom.xml，利用 Docker 缓存依赖层
COPY pom.xml .
COPY knowledge-parse-service/pom.xml knowledge-parse-service/
COPY ai-orchestration-service/pom.xml ai-orchestration-service/
COPY content-gen-service/pom.xml content-gen-service/
COPY spring-cloud-gateway/pom.xml spring-cloud-gateway/

# 2. 复制源码
COPY knowledge-parse-service/src knowledge-parse-service/src
COPY ai-orchestration-service/src ai-orchestration-service/src
COPY content-gen-service/src content-gen-service/src
COPY spring-cloud-gateway/src spring-cloud-gateway/src

# 3. 构建当前模块及其依赖模块
RUN mvn clean package -DskipTests -B -pl spring-cloud-gateway -am

# 运行阶段
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=builder /build/spring-cloud-gateway/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
