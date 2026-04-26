package com.firstticket.userservice.domain.query;

import com.firstticket.userservice.domain.UserRole;
import com.firstticket.userservice.domain.UserStatus;

/**
 * ADMIN 사용자 목록 검색 조건 도메인 DTO
 *
 * 설계 결정 사항
 * - record 로 불변 객체 보장
 * - null 은 "해당 조건 미적용" 을 의미 > Optional 조건 처리
 * - Application layer > UserSearchQuery.toSpec()에서 변환되어 전달
 * - 실제 필터링은 UserQueryRepositoryImpl(Querydsl)에서 담당
 */
public record UserSearchSpec(
    String email,
    String username,
    UserRole role,
    UserStatus status
) {
}
