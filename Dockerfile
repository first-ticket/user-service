# user-service 배포용 Dockerfile
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY gradlew .
COPY gradle/ gradle/
RUN chmod +x gradlew

COPY build.gradle settings.gradle ./

COPY src/ src/

ARG GITHUB_USER

# GITHUB_TOKEN은 BuildKit Secret으로 받음 (이미지에 안 남음)
RUN --mount=type=secret,id=github_token \
    GITHUB_TOKEN="$(cat /run/secrets/github_token)" \
    GITHUB_USER=${GITHUB_USER} \
    ./gradlew bootJar -x test -x asciidoctor --no-daemon

FROM eclipse-temurin:21-jre-alpine

# 보안 강화 - 전용 비루트 사용자 생성
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# 와일드카드로 jar 받기 (이름 명시 안 되어 있을 가능성)
COPY --from=builder /workspace/build/libs/*.jar app.jar

RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
