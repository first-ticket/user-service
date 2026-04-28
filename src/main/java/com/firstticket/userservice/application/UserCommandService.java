package com.firstticket.userservice.application;

import java.util.UUID;

import com.firstticket.userservice.application.dto.command.LoginCommand;
import com.firstticket.userservice.application.dto.command.SignupCommand;
import com.firstticket.userservice.application.dto.result.TokenResult;
import com.firstticket.userservice.application.dto.result.UserResult;
import com.firstticket.userservice.domain.Email;
import com.firstticket.userservice.domain.Password;
import com.firstticket.userservice.domain.User;
import com.firstticket.userservice.domain.UserRepository;
import com.firstticket.userservice.domain.UserStatus;
import com.firstticket.userservice.domain.exception.UserErrorCode;
import com.firstticket.userservice.domain.exception.UserException;
import com.firstticket.userservice.domain.service.KeycloakAuthService;
import com.firstticket.userservice.domain.service.RefreshTokenStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 설계 결정 사항
 * - Application 계층은 domain 객체를 조율하는 역할만 담당 (로직은 Entity/VO 에서)
 * - KeycloakAuthService는 Port 인터페이스를 통해 주입 (infrastructure 직접 의존 금지)
 * - @Transactional: DB작업의 원자성 보장
 *   Keycloak 성공 but DB작업 실패 시 고아 계정 발생 가능 → MVP 개발 완료 후 보완 필요
 * - login은 DB 조회만 하므로 readOnly = true로 최적화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserCommandService {

    private final UserRepository userRepository;
    private final KeycloakAuthService keycloakAuthService;
    private final RefreshTokenStore refreshTokenStore;

    /**
     * 회원가입
     * 처리 흐름
     * 1. VO 생성 → Email / Password 형식 검증 (실패 시 400 Bad Request)
     * 2. 이메일 중복 확인 (실패 시 409 Conflict)
     * 3. Keycloak 계정 생성 → 비밀번호 해시 처리 위임, keycloakId 수신
     * 4. User Entity 생성 / DB 저장
     * 5. UserResult 반환
     */
    @Transactional
    public UserResult signup(SignupCommand command) {
        // 1. VO 생성 (VO 생성자에서 형식 검증 - 실패 시 UserException 발생 → 400)
        Email email = Email.of(command.email());
        Password password = Password.of(command.password()); // 검증 후 Keycloak에 전달하고 버림

        // 2. 이메일 중복 확인 (DB 레벨에서 우선 차단 - Keycloak 호출 전에 확인하여 불필요한 외부 호출 방지)
        if (userRepository.existsByEmail(email.value())) {
            throw new UserException(UserErrorCode.DUPLICATE_EMAIL);
        }

        // 3. Keycloak Admin Client를 통해 Realm에 사용자 생성 (비밀번호 해시 처리 위임)
        // keycloakId = Keycloak 이 발급한 사용자 UUID (JWT sub claim 과 동일)
        String keycloakId = keycloakAuthService.createUser(
            email.value(),
            password.value(),
            command.username()
        );

        // 4. User 엔티티 생성 (role: CUSTOMER 고정, status: ACTIVE 고정)
        User user = User.create(keycloakId, email, command.username());

        // Keycloak 에서 받은 자신의 UUID 를 created_by 에 직접 주입
        user.initCreatedBy(UUID.fromString(keycloakId));

        // 5. DB 저장 후 Result DTO 반환
        User savedUser = userRepository.save(user);
        return UserResult.from(savedUser);
    }

    /**
     * 로그인
     */
    @Transactional(readOnly = true)
    public TokenResult login(LoginCommand command) {
        // 1. 이메일로 사용자 조회
        User user = userRepository.findByEmail(command.email())
            .orElseThrow(() -> {
                log.warn("[login] DB 조회 실패 - email: {}", command.email());
                return new UserException(UserErrorCode.INVALID_CREDENTIALS);
            });

        // 2. 계정 상태 검증 : 'ACTIVE' 이외에는 로그인 실패
        // LOCKED: 잠김 / DELETED : 탈퇴처리
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("[login] 계정 비활성 상태 - userId: {}, status: {}", user.getId(), user.getStatus());
            throw new UserException(UserErrorCode.INVALID_CREDENTIALS);
        }

        // 3. Keycloak Token Endpoint 호출
        TokenResult tokenResult = keycloakAuthService.login(command.email(), command.password());

        // 4. Refresh Token redis 키 저장
        refreshTokenStore.save(user.getId(), tokenResult.refreshToken());

        // 5. AccessToken, RefreshToken 반환
        return tokenResult;
    }
}
