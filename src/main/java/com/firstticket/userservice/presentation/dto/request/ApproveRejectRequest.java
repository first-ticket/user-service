package com.firstticket.userservice.presentation.dto.request;

import com.firstticket.userservice.application.HostRequestAction;
import jakarta.validation.constraints.NotNull;

/**
 * HOST 신청 승인/거절 요청 DTO
 * PATCH /api/v1/admin/host-requests/{requestId}
 * Body: { "action": "APPROVE" } or { "action": "REJECT" }
 */
public record ApproveRejectRequest(

    // Jackson이 "APPROVE"/"REJECT" 문자열을 HostRequestAction enum으로 역직렬화
    @NotNull(message = "action 값은 필수입니다.")
    HostRequestAction action
) {}
