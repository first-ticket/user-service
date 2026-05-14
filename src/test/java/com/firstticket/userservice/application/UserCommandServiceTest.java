package com.firstticket.userservice.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.firstticket.userservice.application.dto.command.ChangePasswordCommand;
import com.firstticket.userservice.application.dto.command.LoginCommand;
import com.firstticket.userservice.application.dto.command.LogoutCommand;
import com.firstticket.userservice.application.dto.command.RefreshTokenCommand;
import com.firstticket.userservice.application.dto.command.SignupCommand;
import com.firstticket.userservice.application.dto.command.UpdateProfileCommand;
import com.firstticket.userservice.application.dto.result.TokenResult;
import com.firstticket.userservice.application.dto.result.UserResult;
import com.firstticket.userservice.domain.Email;
import com.firstticket.userservice.domain.User;
import com.firstticket.userservice.domain.UserRepository;
import com.firstticket.userservice.domain.UserRole;
import com.firstticket.userservice.domain.exception.UserErrorCode;
import com.firstticket.userservice.domain.exception.UserException;
import com.firstticket.userservice.domain.service.AccessTokenBlacklist;
import com.firstticket.userservice.domain.service.AccessTokenCache;
import com.firstticket.userservice.domain.service.KeycloakAuthService;
import com.firstticket.userservice.domain.service.RefreshTokenStore;
import com.firstticket.userservice.domain.service.RefreshTokenStore.RotateResult;

/**
 * UserCommandService 단위 테스트
 *
 * Keycloak, Redis, JPA Repository를 모두 Mock 처리
 * 유즈케이스 흐름과 예외 전파 규칙만 검증
 *
 * VO 유효성 검증(Email, Password)은 Domain 계층 테스트에서 이미 검증하므로
 * 본 테스트에서는 유효한 값을 픽스처로 사용
 */
@ExtendWith(MockitoExtension.class)
class UserCommandServiceTest {

    @InjectMocks
    private UserCommandService userCommandService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private KeycloakAuthService keycloakAuthService;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private AccessTokenBlacklist accessTokenBlacklist;

    @Mock
    private AccessTokenCache accessTokenCache;

    // keycloakId는 Keycloak이 발급하는 UUID 문자열
    private final String KEYCLOAK_ID = UUID.randomUUID().toString();

    // 테스트 전용 ACTIVE User 픽스처
    private User activeUser() {
        return User.create(KEYCLOAK_ID, Email.of("test@example.com"), "testUser");
    }

    // ======== signup ========

    @Nested
    @DisplayName("회원가입(signup)")
    class Signup {

        @Test
        @DisplayName("정상 회원가입 시 UserResult를 반환하고 Keycloak 계정을 생성한다")
        void 정상_회원가입() {
            // given
            SignupCommand command = new SignupCommand("new@example.com", "password1!", "신규유저");

            when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
            when(keycloakAuthService.createUser(anyString(), anyString(), anyString()))
                .thenReturn(KEYCLOAK_ID); // Keycloak이 발급한 keycloakId 반환
            // save() 호출 시 전달받은 User 그대로 반환 (JPA ID 생성 없이 흐름 검증)
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            // when
            UserResult result = userCommandService.signup(command);

            // then — Result 필드 검증
            assertThat(result.email()).isEqualTo("new@example.com");
            assertThat(result.role()).isEqualTo(UserRole.CUSTOMER);  // 신규 가입은 항상 CUSTOMER

            // Keycloak 계정 생성이 정확히 1회 호출되었는지 검증
            verify(keycloakAuthService).createUser("new@example.com", "password1!", "CUSTOMER");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("이미 사용 중인 이메일로 가입 시 DUPLICATE_EMAIL 예외가 발생하며 Keycloak은 호출되지 않는다")
        void 중복_이메일_예외() {
            // given
            SignupCommand command = new SignupCommand("dup@example.com", "password1!", "중복유저");
            when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> userCommandService.signup(command))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.DUPLICATE_EMAIL);

            // DB 중복 확인 이후에 Keycloak 호출이 일어나서는 안 됨 (불필요한 외부 호출 방지)
            verify(keycloakAuthService, never()).createUser(anyString(), anyString(), anyString());
        }
    }

    // ======== login ========

    @Nested
    @DisplayName("로그인(login)")
    class Login {

        @Test
        @DisplayName("정상 로그인 시 TokenResult를 반환하고 Redis에 Refresh Token을 저장한다")
        void 정상_로그인() {
            // given
            LoginCommand command = new LoginCommand("test@example.com", "password1!");
            User user = activeUser();
            TokenResult tokenResult = new TokenResult("access-token", "refresh-token");

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            // 캐시 미스 시나리오: 저장된 AT 없음 → Keycloak 호출 경로 진입
            when(accessTokenCache.find(anyString())).thenReturn(Optional.empty());
            when(keycloakAuthService.login(anyString(), anyString())).thenReturn(tokenResult);

            // when
            TokenResult result = userCommandService.login(command);

            // then
            assertThat(result.accessToken()).isEqualTo("access-token");
            assertThat(result.refreshToken()).isEqualTo("refresh-token");

            verify(refreshTokenStore).save(any(), anyString()); // RT Redis 저장
            verify(accessTokenCache).save(anyString(), anyString()); // AT 캐시 저장
        }

        @Test
        @DisplayName("등록되지 않은 이메일로 로그인 시 INVALID_CREDENTIALS 예외가 발생한다")
        void 사용자_없음_예외() {
            // given
            LoginCommand command = new LoginCommand("nobody@example.com", "password1!");
            when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

            // when & then - 계정 존재 여부를 노출하지 않기 위해 INVALID_CREDENTIALS 단일 에러 반환
            assertThatThrownBy(() -> userCommandService.login(command))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_CREDENTIALS);
        }

        @Test
        @DisplayName("LOCKED 계정으로 로그인 시 INVALID_CREDENTIALS 예외가 발생한다")
        void LOCKED_계정_예외() {
            // given — LOCKED 상태 User 생성
            User lockedUser = activeUser();
            lockedUser.lock();

            LoginCommand command = new LoginCommand("test@example.com", "password1!");
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(lockedUser));

            // when & then
            assertThatThrownBy(() -> userCommandService.login(command))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_CREDENTIALS);
        }

        @Test
        @DisplayName("DELETED 계정으로 로그인 시 INVALID_CREDENTIALS 예외가 발생한다")
        void DELETED_계정_예외() {
            // given — DELETED 상태 User 생성
            User deletedUser = activeUser();
            deletedUser.softDelete(UUID.randomUUID());

            LoginCommand command = new LoginCommand("test@example.com", "password1!");
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(deletedUser));

            // when & then
            assertThatThrownBy(() -> userCommandService.login(command))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_CREDENTIALS);
        }
    }

    // ======== logout ========

    @Nested
    @DisplayName("로그아웃(logout)")
    class Logout {

        // 테스트용 jti — Keycloak이 발급하는 JWT ID 클레임 형태(UUID)
        private static final String JTI = UUID.randomUUID().toString();

        // 테스트용 tokenExpEpoch — 현재로부터 15분 후 (Access Token 기본 TTL)
        // static 필드로 선언하면 클래스 로딩 시점에 1회만 계산되어 일관성 보장
        private static final long FUTURE_EXP = Instant.now().plusSeconds(900).getEpochSecond();

        @Test
        @DisplayName("정상 로그아웃 시 Refresh Token이 삭제되고 Access Token이 blacklist에 등록된다")
        void 정상_로그아웃() {
            // given
            User user = activeUser();
            LogoutCommand command = new LogoutCommand(KEYCLOAK_ID, JTI, FUTURE_EXP);
            when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(user));

            // when
            userCommandService.logout(command);

            // then — Refresh Token 삭제 + Access Token blacklist 등록 순서로 검증
            InOrder inOrder = inOrder(refreshTokenStore, accessTokenBlacklist);
            inOrder.verify(refreshTokenStore).delete(any());
            inOrder.verify(accessTokenBlacklist).add(anyString(), anyLong());
            // AT 캐시 무효화 — 로그아웃 후 stale 토큰 재반환 방지
            verify(accessTokenCache).delete(anyString());
        }

        @Test
        @DisplayName("존재하지 않는 사용자 로그아웃 시 USER_NOT_FOUND 예외가 발생하고 blacklist 등록이 호출되지 않는다")
        void 사용자_없음_예외() {
            // given
            LogoutCommand command = new LogoutCommand(KEYCLOAK_ID, JTI, FUTURE_EXP);
            when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userCommandService.logout(command))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);

            // 사용자가 없으면 Redis 작업이 일어나서는 안 됨
            verify(refreshTokenStore, never()).delete(any());
            verify(accessTokenBlacklist, never()).add(anyString(), anyLong());
        }
    }

    // ======== refreshToken ========

    @Nested
    @DisplayName("토큰 재발급(refreshToken)")
    class RefreshToken {

        @Test
        @DisplayName("정상 재발급 시 새 TokenResult를 반환한다")
        void 정상_재발급() {
            // given
            String oldRefreshToken = "old-refresh-token";
            String newRefreshToken = "new-refresh-token";
            RefreshTokenCommand command = new RefreshTokenCommand(oldRefreshToken);
            User user = activeUser();
            TokenResult newTokens = new TokenResult("new-access-token", newRefreshToken);

            when(keycloakAuthService.extractSubject(oldRefreshToken)).thenReturn(KEYCLOAK_ID);
            when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(user));
            when(keycloakAuthService.refreshToken(oldRefreshToken)).thenReturn(newTokens);
            // Lua CAS rotate 성공 시나리오
            when(refreshTokenStore.rotate(any(), anyString(), anyString())).thenReturn(RotateResult.SUCCESS);

            // when
            TokenResult result = userCommandService.refreshToken(command);

            // then
            assertThat(result.accessToken()).isEqualTo("new-access-token");
            assertThat(result.refreshToken()).isEqualTo(newRefreshToken);
        }

        @Test
        @DisplayName("Redis에 토큰이 없으면(NOT_FOUND) INVALID_REFRESH_TOKEN 예외가 발생한다")
        void 토큰_없음_예외() {
            // given — 로그아웃 후 또는 TTL 만료 후 재발급 시도
            RefreshTokenCommand command = new RefreshTokenCommand("expired-token");
            User user = activeUser();

            when(keycloakAuthService.extractSubject(anyString())).thenReturn(KEYCLOAK_ID);
            when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(user));
            when(keycloakAuthService.refreshToken(anyString()))
                .thenReturn(new TokenResult("a", "b"));
            when(refreshTokenStore.rotate(any(), anyString(), anyString())).thenReturn(RotateResult.NOT_FOUND);

            // when & then
            assertThatThrownBy(() -> userCommandService.refreshToken(command))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_REFRESH_TOKEN);
        }

        @Test
        @DisplayName("토큰 불일치(TOKEN_MISMATCH) 시 INVALID_REFRESH_TOKEN 예외가 발생한다 - 재사용 공격 방어")
        void 토큰_불일치_예외() {
            // given - 이미 rotate된 구 토큰 사용 시도
            RefreshTokenCommand command = new RefreshTokenCommand("stale-token");
            User user = activeUser();

            when(keycloakAuthService.extractSubject(anyString())).thenReturn(KEYCLOAK_ID);
            when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(user));
            when(keycloakAuthService.refreshToken(anyString()))
                .thenReturn(new TokenResult("a", "b"));
            when(refreshTokenStore.rotate(any(), anyString(), anyString())).thenReturn(RotateResult.TOKEN_MISMATCH);

            // when & then
            assertThatThrownBy(() -> userCommandService.refreshToken(command))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    // ======== deleteUser (ADMIN) ========

    @Nested
    @DisplayName("사용자 삭제(deleteUser) - ADMIN")
    class DeleteUser {

        @Test
        @DisplayName("정상 삭제 시 Soft Delete 처리 후 Keycloak 비활성화를 호출한다")
        void 정상_삭제() {
            // given
            UUID adminId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            User user = activeUser();

            when(userRepository.findById(targetId)).thenReturn(Optional.of(user));

            // when
            userCommandService.deleteUser(adminId, targetId);

            // then — Keycloak 비활성화 1회 호출 검증
            verify(keycloakAuthService).disableUser(KEYCLOAK_ID);
        }

        @Test
        @DisplayName("존재하지 않는 사용자 삭제 시 USER_NOT_FOUND 예외가 발생한다")
        void 사용자_없음_예외() {
            // given
            when(userRepository.findById(any())).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userCommandService.deleteUser(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("이미 탈퇴한 사용자 재삭제 시 USER_ALREADY_DELETED 예외가 발생한다")
        void 이미_삭제된_사용자_예외() {
            // given - 이미 DELETED 상태인 User
            User deletedUser = activeUser();
            deletedUser.softDelete(UUID.randomUUID());

            when(userRepository.findById(any())).thenReturn(Optional.of(deletedUser));

            // when & then
            assertThatThrownBy(() -> userCommandService.deleteUser(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_ALREADY_DELETED);
        }
    }

    // ======== changeRole (ADMIN) ========

    @Nested
    @DisplayName("역할 변경(changeRole) — ADMIN")
    class ChangeRole {

        @Test
        @DisplayName("정상 역할 변경 시 DB와 Keycloak 모두 업데이트된다")
        void 정상_역할_변경() {
            // given
            UUID targetId = UUID.randomUUID();
            User user = activeUser(); // role: CUSTOMER

            when(userRepository.findById(targetId)).thenReturn(Optional.of(user));

            // when
            UserResult result = userCommandService.changeRole(targetId, UserRole.HOST);

            // then
            assertThat(result.role()).isEqualTo(UserRole.HOST);
            // 기존 역할(CUSTOMER) 제거 → 새 역할(HOST) 부여 순서로 Keycloak 동기화
            verify(keycloakAuthService).changeUserRole(KEYCLOAK_ID, "CUSTOMER", "HOST");
        }

        @Test
        @DisplayName("존재하지 않는 사용자 역할 변경 시 USER_NOT_FOUND 예외가 발생한다")
        void 사용자_없음_예외() {
            // given
            when(userRepository.findById(any())).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userCommandService.changeRole(UUID.randomUUID(), UserRole.HOST))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
        }
    }

    // ======== updateProfile ========

    @Nested
    @DisplayName("내 정보 수정(updateProfile)")
    class UpdateProfile {

        @Test
        @DisplayName("정상 수정 시 변경된 username으로 UserResult를 반환한다")
        void 정상_수정() {
            // given
            User user = activeUser();
            UpdateProfileCommand command = new UpdateProfileCommand("변경된닉네임");

            when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(user));

            // when
            UserResult result = userCommandService.updateProfile(KEYCLOAK_ID, command);

            // then
            assertThat(result.username()).isEqualTo("변경된닉네임");
        }

        @Test
        @DisplayName("존재하지 않는 사용자 수정 시 USER_NOT_FOUND 예외가 발생한다")
        void 사용자_없음_예외() {
            // given
            when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() ->
                userCommandService.updateProfile(KEYCLOAK_ID, new UpdateProfileCommand("닉네임")))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
        }
    }

    // ======== withdraw ========

    @Nested
    @DisplayName("회원 탈퇴(withdraw)")
    class Withdraw {

        @Test
        @DisplayName("정상 탈퇴 시 Soft Delete, Keycloak 비활성화, Redis 토큰 삭제가 모두 호출된다")
        void 정상_탈퇴() {
            // given
            User user = activeUser();
            when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(user));

            // when
            userCommandService.withdraw(KEYCLOAK_ID);

            // then — 3단계 처리 모두 호출 검증
            verify(keycloakAuthService).disableUser(KEYCLOAK_ID); // Keycloak 비활성화
            verify(refreshTokenStore).delete(any());               // Redis RT 삭제
            verify(accessTokenCache).delete(anyString());          // AT 캐시 무효화
        }

        @Test
        @DisplayName("존재하지 않는 사용자 탈퇴 시 USER_NOT_FOUND 예외가 발생한다")
        void 사용자_없음_예외() {
            // given
            when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userCommandService.withdraw(KEYCLOAK_ID))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("비밀번호 변경(changePassword)")
    class ChangePassword {

        // 테스트 전용 비밀번호 픽스처 (Password VO 최소 요건인 8자 이상 충족)
        private static final String CURRENT_PW = "CurrentPass1!";
        private static final String NEW_PW = "NewPassword1!";

        @Test
        @DisplayName("정상 변경 시 Keycloak 현재 비밀번호 검증 후 새 비밀번호로 변경한다")
        void 정상_비밀번호_변경() {
            // given
            User user = activeUser();
            ChangePasswordCommand command = new ChangePasswordCommand(CURRENT_PW, NEW_PW);

            when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(user));
            when(keycloakAuthService.login(anyString(), anyString()))
                .thenReturn(new TokenResult("access-token", "refresh-token"));

            // when
            userCommandService.changePassword(KEYCLOAK_ID, command);

            // then — 현재 비밀번호 검증 후 변경 흐름 순서 검증
            InOrder inOrder = inOrder(keycloakAuthService);
            inOrder.verify(keycloakAuthService).login(anyString(), anyString());
            inOrder.verify(keycloakAuthService).changePassword(KEYCLOAK_ID, NEW_PW);
            // AT 캐시 무효화 — 비밀번호 변경 후 구 토큰 재사용 방지
            verify(accessTokenCache).delete(anyString());
        }

        @Test
        @DisplayName("존재하지 않는 사용자이면 USER_NOT_FOUND 예외가 발생하고 Keycloak을 호출하지 않는다")
        void 사용자_없음_예외() {
            // given
            when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.empty());
            ChangePasswordCommand command = new ChangePasswordCommand(CURRENT_PW, NEW_PW);

            // when & then
            assertThatThrownBy(() -> userCommandService.changePassword(KEYCLOAK_ID, command))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);

            // 사용자가 없으면 Keycloak 호출이 일어나서는 안 됨 (불필요한 외부 호출 방지)
            verify(keycloakAuthService, never()).login(anyString(), anyString());
            verify(keycloakAuthService, never()).changePassword(anyString(), anyString());
        }

        @Test
        @DisplayName("DELETED 사용자이면 USER_ALREADY_DELETED 예외가 발생한다")
        void 탈퇴된_사용자_예외() {
            // given — softDelete() 호출로 DELETED 상태 User 생성
            User deletedUser = activeUser();
            deletedUser.softDelete(UUID.randomUUID());

            ChangePasswordCommand command = new ChangePasswordCommand(CURRENT_PW, NEW_PW);
            when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(deletedUser));

            // when & then
            assertThatThrownBy(() -> userCommandService.changePassword(KEYCLOAK_ID, command))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_ALREADY_DELETED);

            // 탈퇴 상태 차단 이후 Keycloak 호출 없어야 함
            verify(keycloakAuthService, never()).login(anyString(), anyString());
        }

        @Test
        @DisplayName("현재 비밀번호가 틀리면 INVALID_CREDENTIALS가 WRONG_CURRENT_PASSWORD로 변환되어 발생한다")
        void 현재_비밀번호_불일치_예외() {
            // given - Keycloak ROPC 로그인 실패 시나리오
            User user = activeUser();
            ChangePasswordCommand command = new ChangePasswordCommand("WrongPass1!", NEW_PW);

            when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(user));
            // 현재 비밀번호 검증 실패: INVALID_CREDENTIALS → WRONG_CURRENT_PASSWORD로 래핑
            when(keycloakAuthService.login(anyString(), anyString()))
                .thenThrow(new UserException(UserErrorCode.INVALID_CREDENTIALS));

            // when & then
            assertThatThrownBy(() -> userCommandService.changePassword(KEYCLOAK_ID, command))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.WRONG_CURRENT_PASSWORD);

            // 현재 비밀번호 검증 실패 시 Keycloak 비밀번호 변경은 호출되지 않아야 함
            verify(keycloakAuthService, never()).changePassword(anyString(), anyString());
        }

        @Test
        @DisplayName("Keycloak 비밀번호 변경 API가 실패하면 KEYCLOAK_PASSWORD_CHANGE_FAILED 예외가 발생한다")
        void Keycloak_변경_실패_예외() {
            // given - 현재 비밀번호 검증은 성공, 변경 API에서 장애 발생 시나리오
            User user = activeUser();
            ChangePasswordCommand command = new ChangePasswordCommand(CURRENT_PW, NEW_PW);

            when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(user));
            when(keycloakAuthService.login(anyString(), anyString()))
                .thenReturn(new TokenResult("access-token", "refresh-token"));
            // Keycloak Admin API 호출 실패 — Infrastructure 계층에서 이미 래핑된 예외 전파
            doThrow(new UserException(UserErrorCode.KEYCLOAK_PASSWORD_CHANGE_FAILED))
                .when(keycloakAuthService).changePassword(anyString(), anyString());

            // when & then
            assertThatThrownBy(() -> userCommandService.changePassword(KEYCLOAK_ID, command))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.KEYCLOAK_PASSWORD_CHANGE_FAILED);
        }

        @Test
        @DisplayName("새 비밀번호가 8자 미만이면 INVALID_PASSWORD_FORMAT 예외가 발생하고 Keycloak을 호출하지 않는다")
        void 새_비밀번호_형식_불량_예외() {
            // given
            // @NotBlank(Presentation 계층)는 통과하지만 Password VO 최소 길이(8자)를 위반하는 값
            // 서비스 3단계: Password.of(command.newPassword()) 에서 INVALID_PASSWORD_FORMAT 발생
            User user = activeUser();
            ChangePasswordCommand command = new ChangePasswordCommand(CURRENT_PW, "Short1!"); // 7자

            when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(user));

            // when & then
            assertThatThrownBy(() -> userCommandService.changePassword(KEYCLOAK_ID, command))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_PASSWORD_FORMAT);

            // 형식 검증(3단계) 실패 → 현재 비밀번호 검증(4단계)과 Keycloak 변경(5단계) 모두 호출 금지
            verify(keycloakAuthService, never()).login(anyString(), anyString());
            verify(keycloakAuthService, never()).changePassword(anyString(), anyString());
        }
    }
}
