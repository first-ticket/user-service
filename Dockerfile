# user-service 배포용 Dockerfile
# 전제: GitHub Actions에서 ./gradlew build 완료 후 이미지 빌드
# 결과물: build/libs/app.jar (bootJar archiveFileName 고정)
# 배포 대상: AWS ECS Fargate (ECR 이미지 사용)

# 1. 베이스 이미지
FROM eclipse-temurin:21-jre-alpine

# 2. 보안 강화 - 전용 비루트 사용자 생성
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 3. 작업 디렉토리 설정
# /app: 앱 전용 디렉토리. root 소유 파일이 /root에 섞이지 않도록 분리
WORKDIR /app

# 4. JAR 복사
COPY build/libs/app.jar app.jar

# 5. 파일 소유권 변경
RUN chown appuser:appgroup app.jar

USER appuser

# 7. 포트 선언
EXPOSE 8080

# 8. JVM 실행 옵션
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
