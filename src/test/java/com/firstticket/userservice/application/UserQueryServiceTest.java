package com.firstticket.userservice.application;

import com.firstticket.userservice.application.dto.result.UserResult;
import com.firstticket.userservice.domain.Email;
import com.firstticket.userservice.domain.User;
import com.firstticket.userservice.domain.UserRepository;
import com.firstticket.userservice.domain.exception.UserErrorCode;
import com.firstticket.userservice.domain.exception.UserException;
import com.firstticket.userservice.domain.query.UserQueryRepository;
import com.firstticket.userservice.domain.query.UserSearchSpec;
import com.firstticket.userservice.domain.query.UserSummaryData;
import com.firstticket.userservice.domain.UserRole;
import com.firstticket.userservice.domain.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceTest {

    @InjectMocks
    private UserQueryService userQueryService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserQueryRepository userQueryRepository;

    private final UUID KEYCLOAK_UUID = UUID.randomUUID();
    private final String KEYCLOAK_ID = KEYCLOAK_UUID.toString();

    private User activeUser() {
        return User.create(KEYCLOAK_ID, Email.of("test@example.com"), "testUser");
    }

    // ======== getMyInfo ========

    @Nested
    @DisplayName("내 정보 조회(getMyInfo)")
    class GetMyInfo {

        @Test
        @DisplayName("정상 조회 시 UserResult를 반환한다")
        void 정상_조회() {
            // given
            User user = activeUser();
            when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(user));

            // when
            UserResult result = userQueryService.getMyInfo(KEYCLOAK_UUID);

            // then
            assertThat(result.email()).isEqualTo("test@example.com");
            assertThat(result.username()).isEqualTo("testUser");
        }

        @Test
        @DisplayName("존재하지 않는 사용자 조회 시 USER_NOT_FOUND 예외가 발생한다")
        void 사용자_없음_예외() {
            // given
            when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userQueryService.getMyInfo(KEYCLOAK_UUID))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("DELETED 상태 사용자 조회 시 USER_NOT_FOUND 예외가 발생한다")
        void DELETED_사용자_예외() {
            // given — 탈퇴 처리된 사용자도 DB에 존재하지만 조회 거부
            User deletedUser = activeUser();
            deletedUser.softDelete(UUID.randomUUID());
            when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(deletedUser));

            // when & then
            assertThatThrownBy(() -> userQueryService.getMyInfo(KEYCLOAK_UUID))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
        }
    }

    // ======== getUserById (ADMIN) ========

    @Nested
    @DisplayName("사용자 단건 조회(getUserById) — ADMIN")
    class GetUserById {

        @Test
        @DisplayName("존재하는 사용자 조회 시 UserSummaryData를 반환한다")
        void 정상_조회() {
            // given
            UUID userId = UUID.randomUUID();
            UserSummaryData summaryData = new UserSummaryData(
                userId, "test@example.com", "testUser",
                UserRole.CUSTOMER, UserStatus.ACTIVE, LocalDateTime.now()
            );
            when(userQueryRepository.findSummaryById(userId)).thenReturn(Optional.of(summaryData));

            // when
            UserSummaryData result = userQueryService.getUserById(userId);

            // then
            assertThat(result.email()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("존재하지 않는 사용자 조회 시 USER_NOT_FOUND 예외가 발생한다")
        void 사용자_없음_예외() {
            // given
            when(userQueryRepository.findSummaryById(any())).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userQueryService.getUserById(UUID.randomUUID()))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
        }
    }

    // ======== getUsers (ADMIN) ========

    @Nested
    @DisplayName("사용자 목록 조회(getUsers) — ADMIN")
    class GetUsers {

        @Test
        @DisplayName("Querydsl 동적 검색 결과를 그대로 반환한다")
        void 목록_조회_위임() {
            // given — Querydsl 구현체에 위임하는지 검증 (동적 쿼리 자체는 Infrastructure 테스트에서 검증)
            Pageable pageable = PageRequest.of(0, 10);
            UserSummaryData data = new UserSummaryData(
                UUID.randomUUID(), "a@example.com", "유저A",
                UserRole.CUSTOMER, UserStatus.ACTIVE, LocalDateTime.now()
            );
            Page<UserSummaryData> mockPage = new PageImpl<>(List.of(data), pageable, 1);

            when(userQueryRepository.searchUsers(any(UserSearchSpec.class), any(Pageable.class)))
                .thenReturn(mockPage);

            // when
            Page<UserSummaryData> result = userQueryService.getUsers(
                new UserSearchSpec(null, null, null,null), pageable
            );

            // then
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).email()).isEqualTo("a@example.com");
        }
    }
}
