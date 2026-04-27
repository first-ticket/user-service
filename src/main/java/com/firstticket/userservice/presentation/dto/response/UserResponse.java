package com.firstticket.userservice.presentation.dto.response;

import com.firstticket.userservice.application.dto.result.UserResult;
import com.firstticket.userservice.domain.UserRole;
import com.firstticket.userservice.domain.UserStatus;

import java.util.UUID;

/**
 * 사용자 정보 Response DTO
 *
 * 설계 결정 사항
 * - UserResult(Application DTO) 를 그대로 반환하지 않고 별도 Response DTO 를 사용
 *   → Application 계층과 Presentation 계층의 결합도 분리
 *   → 향후 API 응답 형식 변경 시 Application 계층에 영향 없음
 * - keycloakId, deletedAt, deletedBy 등 민감/내부 정보 제외
 */
public record UserResponse(
    UUID id,
    String email,
    String username,
    UserRole role,
    UserStatus status
) {

    /**
     * UserResult → UserResponse 변환 정적 팩토리 메서드
     */
    public static UserResponse from(UserResult result) {
        return new UserResponse(
            result.id(),
            result.email(),
            result.username(),
            result.role(),
            result.status()
        );
    }
}
