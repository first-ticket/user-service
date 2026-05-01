package com.firstticket.userservice.domain;

import com.firstticket.userservice.domain.exception.UserErrorCode;
import com.firstticket.userservice.domain.exception.UserException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HostRequestStatusTest {

    @Nested
    @DisplayName("PENDING 상태 전이")
    class PendingTransition {

        @Test
        @DisplayName("PENDING → APPROVED 전이는 허용된다")
        void pending_to_approved_허용() {
            assertThat(HostRequestStatus.PENDING.canTransitionTo(HostRequestStatus.APPROVED)).isTrue();
        }

        @Test
        @DisplayName("PENDING → REJECTED 전이는 허용된다")
        void pending_to_rejected_허용() {
            assertThat(HostRequestStatus.PENDING.canTransitionTo(HostRequestStatus.REJECTED)).isTrue();
        }

        @Test
        @DisplayName("PENDING → PENDING 전이는 허용되지 않아 INVALID_HOST_REQUEST_STATUS 예외가 발생한다")
        void pending_to_pending_불허() {
            assertThatThrownBy(() -> HostRequestStatus.PENDING.validateNext(HostRequestStatus.PENDING))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_HOST_REQUEST_STATUS);
        }
    }

    @Nested
    @DisplayName("APPROVED 상태 전이")
    class ApprovedTransition {

        @Test
        @DisplayName("APPROVED는 최종 상태로 모든 전이가 허용되지 않는다")
        void approved_모든_전이_불허() {
            for (HostRequestStatus next : HostRequestStatus.values()) {
                assertThat(HostRequestStatus.APPROVED.canTransitionTo(next))
                    .as("APPROVED → %s 전이는 허용되어서는 안 됨", next)
                    .isFalse();
            }
        }
    }

    @Nested
    @DisplayName("REJECTED 상태 전이")
    class RejectedTransition {

        @Test
        @DisplayName("REJECTED는 최종 상태로 모든 전이가 허용되지 않는다")
        void rejected_모든_전이_불허() {
            for (HostRequestStatus next : HostRequestStatus.values()) {
                assertThat(HostRequestStatus.REJECTED.canTransitionTo(next))
                    .as("REJECTED → %s 전이는 허용되어서는 안 됨", next)
                    .isFalse();
            }
        }
    }
}
