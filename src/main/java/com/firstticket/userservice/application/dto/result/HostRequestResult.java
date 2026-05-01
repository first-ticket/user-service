package com.firstticket.userservice.application.dto.result;

import com.firstticket.userservice.domain.HostRequest;
import com.firstticket.userservice.domain.HostRequestStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record HostRequestResult(
    UUID id,
    UUID userId,
    HostRequestStatus status,
    LocalDateTime createdAt  // BaseEntity.createdAt
) {
    public static HostRequestResult from(HostRequest hostRequest) {
        return new HostRequestResult(
            hostRequest.getId(),
            hostRequest.getUserId(),
            hostRequest.getStatus(),
            hostRequest.getCreatedAt()
        );
    }
}
