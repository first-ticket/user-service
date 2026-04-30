package com.firstticket.userservice.presentation.dto.request;

import com.firstticket.userservice.domain.UserRole;
import com.firstticket.userservice.domain.UserStatus;
import com.firstticket.userservice.domain.query.UserSearchSpec;

/**
 * ADMIN 사용자 목록 검색 요청 DTO (Query Parameter)
 *
 * 설계 결정 사항
 * - @ModelAttribute 방식으로 쿼리 파라미터를 바인딩
 * - null 값은 "해당 조건 미적용"을 의미 → toSpec() 변환 시 그대로 전달
 * - Presentation → Application 계층 변환은 toSpec()에서 수행
 *   (컨트롤러가 도메인 객체를 직접 다루지 않도록 분리)
 */
public record UserSearchRequest(
    // 이메일 부분 일치 검색 조건 (null이면 전체 검색)
    String email,

    // 사용자명 부분 일치 검색 조건 (null이면 전체 검색)
    String username,

    // 역할 정확 일치 필터 (null이면 역할 무관)
    UserRole role,

    // 상태 정확 일치 필터 (null이면 상태 무관)
    UserStatus status
) {

    /**
     * Presentation DTO → Domain Query 조건 객체로 변환
     * Application 계층에서 사용하는 UserSearchSpec(도메인 객체)을 생성합니다.
     */
    public UserSearchSpec toSpec() {
        return new UserSearchSpec(email, username, role, status);
    }
}
