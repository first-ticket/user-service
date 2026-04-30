package com.firstticket.userservice.presentation.dto.request;

import com.firstticket.userservice.domain.UserRole;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(

    // 변경할 역할 값 — 필수 입력 (null 허용 안 함)
    @NotNull(message = "변경할 role을 입력해 주세요.")
    UserRole role
) {
}
