package com.firstticket.userservice.presentation.dto.response;

import com.firstticket.userservice.domain.UserRole;
import com.firstticket.userservice.domain.UserStatus;
import com.firstticket.userservice.domain.query.UserSummaryData;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminUserResponse(
    // 사용자 PK
    UUID id,

    // 이메일
    String email,

    // 사용자명
    String username,

    // 현재 역할 (CUSTOMER / HOST / ADMIN)
    UserRole role,

    // 현재 상태 (ACTIVE / LOCKED / DELETED)
    UserStatus status,

    // 가입 일시 - 관리자 화면에서 정렬/필터 기준으로 활용
    LocalDateTime createdAt
) {

    public static AdminUserResponse from(UserSummaryData data) {
        return new AdminUserResponse(
            data.id(),
            data.email(),
            data.username(),
            data.role(),
            data.status(),
            data.createdAt()
        );
    }
}
