package com.firstticket.userservice.application.dto.result;

import com.firstticket.userservice.domain.User;
import com.firstticket.userservice.domain.UserRole;
import com.firstticket.userservice.domain.UserStatus;

import java.util.UUID;

/**
 * Application 계층의 사용자 조회 결과 DTO
 *
 * 설계 결정 사항
 * - Domain Entity(User)를 그대로 Presentation계층에 노출하지 않음
 *   → 계층 간 결합도 낮게, Entity 변경 시 Presentation 영향 최소화
 * - 민감 정보(keycloakId, deletedAt등) 포함하지 않음
 */
public record UserResult(
    UUID id,
    String email,
    String username,
    UserRole role,
    UserStatus status
) {

    /**
     * User Entity → UserResult 변환 정적 팩토리 메서드
     * Application 계층에서 호출하며, Service가 직접 Presentation DTO 를 만들지 않도록 분리함
     */
    public static UserResult from(User user) {
        return new UserResult(
            user.getId(),
            user.getEmail(),
            user.getUsername(),
            user.getRole(),
            user.getStatus()
        );
    }
}
