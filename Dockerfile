# user-service 배포용 Dockerfile
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY gradlew .
COPY gradle/ gradle/
RUN chmod +x gradlew

COPY build.gradle settings.gradle ./

COPY src/ src/

ARG GITHUB_USER
ARG GITHUB_TOKEN

RUN GITHUB_USER=${GITHUB_USER} GITHUB_TOKEN=${GITHUB_TOKEN} \
    ./gradlew bootJar -x test -x asciidoctor --no-daemon
FROM eclipse-temurin:21-jre-alpine

# 보안 강화 - 전용 비루트 사용자 생성
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /workspace/build/libs/app.jar app.jar

RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
