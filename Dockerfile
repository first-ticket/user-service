FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew

ARG GITHUB_USER

RUN --mount=type=secret,id=github_token \
    GITHUB_TOKEN="$(cat /run/secrets/github_token)" \
    GITHUB_USER=$GITHUB_USER \
    ./gradlew bootJar -x test -x asciidoctor --no-daemon || true

COPY src src

RUN --mount=type=secret,id=github_token \
    GITHUB_TOKEN="$(cat /run/secrets/github_token)" \
    GITHUB_USER=$GITHUB_USER \
    ./gradlew clean bootJar -x test -x asciidoctor --no-daemon

RUN java -Djarmode=layertools -jar build/libs/*.jar extract

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

RUN useradd -ms /bin/bash spring

WORKDIR /app

COPY --from=builder --chown=spring:spring /app/dependencies/ ./
COPY --from=builder --chown=spring:spring /app/spring-boot-loader/ ./
COPY --from=builder --chown=spring:spring /app/snapshot-dependencies/ ./
COPY --from=builder --chown=spring:spring /app/application/ ./

USER spring

EXPOSE 8080

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
