package com.firstticket.userservice.presentation.dto.response;

import com.firstticket.userservice.application.dto.result.HostRequestResult;
import com.firstticket.userservice.domain.HostRequestStatus;

import java.time.LocalDateTime;
import java.util.UUID;

// HOST 신청 Presentation 응답 DTO
public record HostRequestResponse(
    UUID id,
    UUID userId,
    HostRequestStatus status,
    LocalDateTime createdAt
) {
    public static HostRequestResponse from(HostRequestResult result) {
        return new HostRequestResponse(
            result.id(),
            result.userId(),
            result.status(),
            result.createdAt()
        );
    }
}
