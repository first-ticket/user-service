package com.firstticket.userservice.domain.query;

import com.firstticket.userservice.domain.UserRole;
import com.firstticket.userservice.domain.UserStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 사용자 목록 조회 결과 Projection DTO
 *
 * 설계 결정 사항
 * - record 의 canonical constructor 를 Querydsl Projections.constructor() 가 사용
 *   → 필드 선언 순서와 constructor 파라미터 순서가 일치해야 함
 * - 민감 정보(keycloakId, deletedAt 등)는 포함하지 않습니다.
 * - Application 계층에서 UserSummaryResult 로 변환되어 외부에 노출
 */
public record UserSummaryData(
    UUID id,
    String email,
    String username,
    UserRole role,
    UserStatus status,
    LocalDateTime createdAt
) {
}
