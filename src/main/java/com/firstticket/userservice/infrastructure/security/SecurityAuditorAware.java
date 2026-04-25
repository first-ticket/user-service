package com.firstticket.userservice.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA Auditing 의 createdBy / updatedBy 를 자동으로 채워주는 구현체
 *
 * 주요 동작 흐름
 * 1. API Gateway에서 Keycloak JWT를 검증 후 X-User-Id 헤더에 UUID 주입
 * 2. 각 요청이 user-service에 도달하면, 해당 클래스가 헤더에서 UUID 추출 (요청자 식별)
 * 3. JPA Auditing 인프라가 @CreatedBy / @LastModifiedBy 컬럼에 해당 UUID를 자동으로 기록
 *
 * 등록 방식
 * - common 모듈의 JpaConfig 에 @EnableJpaAuditing이 선언되어 있음
 * - Spring은 ApplicationContext 에서 AuditorAware<UUID> 타입 빈을 자동으로 감지하여 사용
 * - 별도 auditorAwareRef 지정 없이 @Component 등록으로 동작
 */
@Slf4j
@Component
public class SecurityAuditorAware implements AuditorAware<UUID> {

    // API Gateway가 JWT 검증 후 삽입하는 사용자 ID 헤더 이름
    private static final String USER_ID_HEADER = "X-User-Id";

    /**
     * 현재 요청의 X-User-Id 헤더에서 UUID 를 추출하여 반환.
     *
     * Optional.empty() 를 반환하는 경우:
     * - HTTP 요청 컨텍스트가 없는 경우 (Flyway 마이그레이션, 테스트, 비동기처리 등)
     * - X-User-Id 헤더가 없거나 비어 있는 경우
     * - UUID 형식이 아닌 값이 헤더에 들어온 경우
     *
     * 주의사항: BaseUserEntity.created_by 는 NOT NULL 제약조건이 있으므로
     * 실제 API 요청에서는 Gateway 가 항상 X-User-Id를 주입해야 함
     */
    @Override
    public Optional<UUID> getCurrentAuditor() {
        try {
            // Spring MVC RequestContextHolder에서 현재 HTTP 요청 컨텍스트 획득
            // HTTP 요청 스레드가 아닌 경우(스케줄러, Flyway 등) IllegalStateException 발생
            ServletRequestAttributes attributes = (ServletRequestAttributes)
                RequestContextHolder.currentRequestAttributes();

            HttpServletRequest request = attributes.getRequest();

            // X-User-Id 헤더 값 추출
            String userId = request.getHeader(USER_ID_HEADER);

            if (userId == null || userId.isBlank()) {
                // 헤더 없음 - 게이트웨이를 거치지 않은 내부 호출 또는 설정 오류
                return Optional.empty();
            }

            // UUID 형식으로 파싱 후 반환
            return Optional.of(UUID.fromString(userId));

        } catch (IllegalStateException e) {
            // HTTP 요청 컨텍스트 없음 (Flyway, 테스트, 비동기 이벤트 처리 등 정상 케이스)
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            // X-User-Id 헤더 값이 UUID 형식이 아닌 경우 - 게이트웨이 설정 오류
            log.warn("X-User-Id 헤더가 유효한 UUID 형식이 아닙니다. 값을 확인해주세요.");
            return Optional.empty();
        }
    }
}
