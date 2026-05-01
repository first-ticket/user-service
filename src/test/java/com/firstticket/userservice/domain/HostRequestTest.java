package com.firstticket.userservice.domain;

import com.firstticket.userservice.domain.exception.UserErrorCode;
import com.firstticket.userservice.domain.exception.UserException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HostRequestTest {

    @Nested
    @DisplayName("생성(create)")
    class Create {

        @Test
        @DisplayName("생성 시 초기 상태는 PENDING이다")
        void 생성_시_PENDING_상태() {
            HostRequest request = HostRequest.create(UUID.randomUUID());

            assertThat(request.getStatus()).isEqualTo(HostRequestStatus.PENDING);
        }

        @Test
        @DisplayName("생성 시 userId가 올바르게 저장된다")
        void 생성_시_userId_저장() {
            UUID userId = UUID.randomUUID();

            HostRequest request = HostRequest.create(userId);

            assertThat(request.getUserId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("userId가 null이면 NullPointerException이 발생한다")
        void null_userId_예외() {
            // Objects.requireNonNull() 사용 - NPE 명시적 발생
            assertThatThrownBy(() -> HostRequest.create(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("승인(approve)")
    class Approve {

        @Test
        @DisplayName("PENDING 상태에서 approve() 호출 시 APPROVED로 전이된다")
        void pending에서_approve_성공() {
            HostRequest request = HostRequest.create(UUID.randomUUID());

            request.approve();

            assertThat(request.getStatus()).isEqualTo(HostRequestStatus.APPROVED);
        }

        @Test
        @DisplayName("이미 APPROVED 상태에서 approve() 호출 시 INVALID_HOST_REQUEST_STATUS 예외가 발생한다")
        void approved에서_approve_예외() {
            HostRequest request = HostRequest.create(UUID.randomUUID());
            request.approve();

            assertThatThrownBy(request::approve)
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_HOST_REQUEST_STATUS);
        }

        @Test
        @DisplayName("REJECTED 상태에서 approve() 호출 시 INVALID_HOST_REQUEST_STATUS 예외가 발생한다")
        void rejected에서_approve_예외() {
            HostRequest request = HostRequest.create(UUID.randomUUID());
            request.reject();

            assertThatThrownBy(request::approve)
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_HOST_REQUEST_STATUS);
        }
    }

    @Nested
    @DisplayName("거절(reject)")
    class Reject {

        @Test
        @DisplayName("PENDING 상태에서 reject() 호출 시 REJECTED로 전이된다")
        void pending에서_reject_성공() {
            HostRequest request = HostRequest.create(UUID.randomUUID());

            request.reject();

            assertThat(request.getStatus()).isEqualTo(HostRequestStatus.REJECTED);
        }

        @Test
        @DisplayName("이미 REJECTED 상태에서 reject() 호출 시 INVALID_HOST_REQUEST_STATUS 예외가 발생한다")
        void rejected에서_reject_예외() {
            HostRequest request = HostRequest.create(UUID.randomUUID());
            request.reject();

            assertThatThrownBy(request::reject)
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_HOST_REQUEST_STATUS);
        }

        @Test
        @DisplayName("APPROVED 상태에서 reject() 호출 시 INVALID_HOST_REQUEST_STATUS 예외가 발생한다")
        void approved에서_reject_예외() {
            HostRequest request = HostRequest.create(UUID.randomUUID());
            request.approve();

            assertThatThrownBy(request::reject)
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_HOST_REQUEST_STATUS);
        }
    }
}
