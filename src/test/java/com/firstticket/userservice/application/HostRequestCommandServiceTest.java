package com.firstticket.userservice.application;

import com.firstticket.userservice.application.dto.result.HostRequestResult;
import com.firstticket.userservice.domain.Email;
import com.firstticket.userservice.domain.HostRequest;
import com.firstticket.userservice.domain.HostRequestRepository;
import com.firstticket.userservice.domain.HostRequestStatus;
import com.firstticket.userservice.domain.User;
import com.firstticket.userservice.domain.UserRepository;
import com.firstticket.userservice.domain.UserRole;
import com.firstticket.userservice.domain.exception.UserErrorCode;
import com.firstticket.userservice.domain.exception.UserException;
import com.firstticket.userservice.domain.service.KeycloakAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HostRequestCommandServiceTest {

    @InjectMocks
    private HostRequestCommandService hostRequestCommandService;

    @Mock
    private HostRequestRepository hostRequestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private KeycloakAuthService keycloakAuthService;

    private final String KEYCLOAK_ID = UUID.randomUUID().toString();
    private final UUID KEYCLOAK_UUID = UUID.fromString(KEYCLOAK_ID);

    // CUSTOMER 역할의 ACTIVE User 픽스처
    private User customerUser() {
        return User.create(KEYCLOAK_ID, Email.of("customer@example.com"), "고객유저");
    }

    // ======== request ========

    @Nested
    @DisplayName("HOST 신청(request)")
    class Request {

        @Test
        @DisplayName("CUSTOMER 사용자가 최초 신청 시 PENDING 상태의 HostRequestResult를 반환한다")
        void 정상_신청() {
            // given
            User user = customerUser();
            // JPA @GeneratedValue가 단위 테스트에서 실행되지 않아 id=null
            // → HostRequest.create(null) 에서 NPE 발생하므로 ReflectionTestUtils로 직접 주입
            UUID userId = UUID.randomUUID();
            ReflectionTestUtils.setField(user, "id", userId);

            HostRequest savedRequest = HostRequest.create(UUID.randomUUID()); // save()가 반환할 픽스처

            when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(user));
            when(hostRequestRepository.existsByUserIdAndStatus(any(), any())).thenReturn(false);
            when(hostRequestRepository.save(any(HostRequest.class))).thenReturn(savedRequest);

            // when
            HostRequestResult result = hostRequestCommandService.request(KEYCLOAK_UUID);

            // then
            assertThat(result.status()).isEqualTo(HostRequestStatus.PENDING);
        }

        @Test
        @DisplayName("존재하지 않는 사용자가 신청 시 USER_NOT_FOUND 예외가 발생한다")
        void 사용자_없음_예외() {
            // given
            when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> hostRequestCommandService.request(KEYCLOAK_UUID))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("CUSTOMER 이외 역할(HOST)이 신청 시 HOST_REQUEST_ONLY_FOR_CUSTOMER 예외가 발생한다")
        void CUSTOMER_아닌_역할_예외() {
            // given — HOST 역할 User
            User hostUser = customerUser();
            hostUser.changeRole(UserRole.HOST);

            when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(hostUser));

            // when & then
            assertThatThrownBy(() -> hostRequestCommandService.request(KEYCLOAK_UUID))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.HOST_REQUEST_ONLY_FOR_CUSTOMER);

            // 중복 확인 단계에 도달하지 않아야 함
            verify(hostRequestRepository, never()).existsByUserIdAndStatus(any(), any());
        }

        @Test
        @DisplayName("이미 PENDING 신청이 있는 경우 HOST_REQUEST_ALREADY_PENDING 예외가 발생한다")
        void 중복_PENDING_예외() {
            // given
            User user = customerUser();

            when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(user));
            when(hostRequestRepository.existsByUserIdAndStatus(any(), any())).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> hostRequestCommandService.request(KEYCLOAK_UUID))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.HOST_REQUEST_ALREADY_PENDING);

            // 중복 확인 이후 save()는 호출되지 않아야 함
            verify(hostRequestRepository, never()).save(any());
        }
    }

    // ======== approveOrReject ========

    @Nested
    @DisplayName("HOST 신청 처리(approveOrReject) — ADMIN")
    class ApproveOrReject {

        @Test
        @DisplayName("PENDING 신청을 승인하면 APPROVED 상태로 전이되고 User 역할이 HOST로 변경된다")
        void 정상_승인() {
            // given
            UUID requestId = UUID.randomUUID();
            UUID adminId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            HostRequest hostRequest = HostRequest.create(userId); // status: PENDING, userId 지정
            User user = customerUser();                            // role: CUSTOMER

            when(hostRequestRepository.findById(requestId)).thenReturn(Optional.of(hostRequest));
            // hostRequest.getUserId() == userId로 User 조회
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when
            HostRequestResult result = hostRequestCommandService.approveOrReject(
                requestId, HostRequestAction.APPROVE, adminId
            );

            // then
            assertThat(result.status()).isEqualTo(HostRequestStatus.APPROVED);
            // User role이 CUSTOMER → HOST로 변경되어야 함
            assertThat(user.getRole()).isEqualTo(UserRole.HOST);
            // Keycloak role 동기화 호출 검증
            verify(keycloakAuthService).changeUserRole(KEYCLOAK_ID, "CUSTOMER", "HOST");
        }

        @Test
        @DisplayName("PENDING 신청을 거절하면 REJECTED 상태로 전이되고 User 역할은 변경되지 않는다")
        void 정상_거절() {
            // given
            UUID requestId = UUID.randomUUID();
            UUID adminId = UUID.randomUUID();
            HostRequest hostRequest = HostRequest.create(UUID.randomUUID()); // status: PENDING

            when(hostRequestRepository.findById(requestId)).thenReturn(Optional.of(hostRequest));

            // when
            HostRequestResult result = hostRequestCommandService.approveOrReject(
                requestId, HostRequestAction.REJECT, adminId
            );

            // then
            assertThat(result.status()).isEqualTo(HostRequestStatus.REJECTED);
            // REJECT 처리 시 User 조회 및 Keycloak 호출이 없어야 함
            verify(userRepository, never()).findById(any());
            verify(keycloakAuthService, never()).changeUserRole(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("존재하지 않는 신청 ID로 처리 시 HOST_REQUEST_NOT_FOUND 예외가 발생한다")
        void 신청_없음_예외() {
            // given
            when(hostRequestRepository.findById(any())).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> hostRequestCommandService.approveOrReject(
                UUID.randomUUID(), HostRequestAction.APPROVE, UUID.randomUUID()))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.HOST_REQUEST_NOT_FOUND);
        }

        @Test
        @DisplayName("이미 APPROVED된 신청을 재승인 시 INVALID_HOST_REQUEST_STATUS 예외가 발생한다")
        void 이미_승인된_신청_재승인_예외() {
            // given — 이미 APPROVED 상태인 HostRequest
            UUID requestId = UUID.randomUUID();
            HostRequest approvedRequest = HostRequest.create(UUID.randomUUID());
            approvedRequest.approve(); // PENDING → APPROVED

            when(hostRequestRepository.findById(requestId)).thenReturn(Optional.of(approvedRequest));

            // when & then — APPROVED → APPROVED 전이 불가
            assertThatThrownBy(() -> hostRequestCommandService.approveOrReject(
                requestId, HostRequestAction.APPROVE, UUID.randomUUID()))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_HOST_REQUEST_STATUS);
        }
    }
}
