package com.firstticket.userservice.application;

import java.util.UUID;

import com.firstticket.userservice.application.dto.command.LoginCommand;
import com.firstticket.userservice.application.dto.command.RefreshTokenCommand;
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
 * - Application 계층은 domain 객체를 조율하는 역할만 담당 (로직은 Entity / VO 에서)
 * - KeycloakAuthService / RefreshTokenStore 는 Port 인터페이스를 통해 주입 (infrastructure 직접 의존 금지)
 * - login() / logout() / refreshToken()은 DB 조회만 수행하므로 readOnly = true 로 최적화
 *   (Redis 쓰기는 JPA 트랜잭션과 무관하게 동작)
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
     * 3. Keycloak 계정 생성 -> 비밀번호 해시 처리 위임, keycloakId 수신
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
     * 처리 흐름
     * 1. 이메일로 DB에서 사용자 조회 (없으면 401)
     * 2. 계정 상태 검증 - ACTIVE 이외에는 로그인 거부
     * 3. Keycloak ROPC 호출 → Access Token + Refresh Token 발급
     * 4. Refresh Token Redis 저장
     * 5. TokenResult 반환
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

        // 4. Refresh Token Redis 저장
        refreshTokenStore.save(user.getId(), tokenResult.refreshToken());

        // 5. AccessToken, RefreshToken 반환
        return tokenResult;
    }

    /**
     * 로그아웃
     * 처리 흐름
     * 1. Gateway X-User-Id 헤더(keycloakId)로 사용자 조회
     * 2. Redis에서 Refresh Token 삭제 (이후 토큰 재발급 불가)
     *
     * 설계 결정 사항
     * - Access Token은 만료될 때까지 유효하지만 TTL이 짧으므로(통상 5~15분) 허용 범위로 간주
     * - Keycloak 세션 revoke는 MVP 범위 외 (추후 /logout 엔드포인트 호출로 보완 가능)
     */
    @Transactional(readOnly = true)
    public void logout(String keycloakId) {
        // 1. keycloakId로 사용자 조회 (없으면 이미 삭제된 계정 또는 비정상 요청)
        User user = userRepository.findByKeycloakId(keycloakId)
            .orElseThrow(() -> {
                log.warn("[logout] 사용자 조회 실패 - keycloakId: {}", keycloakId);
                return new UserException(UserErrorCode.USER_NOT_FOUND);
            });

        // 2. Redis에서 Refresh Token 삭제 → 이후 재발급 불가
        refreshTokenStore.delete(user.getId());
        log.info("[logout] Refresh Token 삭제 완료 - userId: {}", user.getId());
    }

    /**
     * 토큰 재발급 (Token Rotation)
     * 처리 흐름
     * 1. Refresh Token JWT payload에서 sub(keycloakId) 추출 (서명 검증 없이 payload만 디코딩)
     * 2. keycloakId로 사용자 조회 + 상태 확인 (ACTIVE 필수)
     * 3. Redis에서 저장된 Refresh Token 조회 → 없으면 로그아웃된 세션
     * 4. 제공된 토큰 vs 저장된 토큰 비교 → 불일치 시 재사용 공격으로 간주하여 세션 전체 무효화
     * 5. Keycloak에 Refresh Token Grant 요청 → 신규 토큰 쌍 발급
     * 6. Redis rotate → 신규 Refresh Token으로 교체
     * 7. 새 토큰 반환
     */
    @Transactional(readOnly = true)
    public TokenResult refreshToken(RefreshTokenCommand command) {
        // 1. Refresh Token JWT payload에서 sub 추출
        String keycloakId = keycloakAuthService.extractSubject(command.refreshToken());

        // 2. keycloakId로 사용자 조회
        User user = userRepository.findByKeycloakId(keycloakId)
            .orElseThrow(() -> {
                log.warn("[refreshToken] 사용자 조회 실패 - keycloakId: {}", keycloakId);
                return new UserException(UserErrorCode.INVALID_REFRESH_TOKEN);
            });

        // ACTIVE 이외의 계정은 토큰 재발급 거부 (LOCKED, DELETED 포함)
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("[refreshToken] 계정 비활성 상태 - userId: {}, status: {}", user.getId(), user.getStatus());
            throw new UserException(UserErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 3. Redis에서 저장된 Refresh Token 조회
        // 토큰이 없다면 로그아웃 또는 TTL 만료 -> 재로그인 필요
        String storedToken = refreshTokenStore.find(user.getId())
            .orElseThrow(() -> {
                log.warn("[refreshToken] Redis에 토큰 없음 (로그아웃 또는 만료) - userId: {}", user.getId());
                return new UserException(UserErrorCode.INVALID_REFRESH_TOKEN);
            });

        // 4. 제공된 토큰과 저장된 토큰 비교
        // 불일치: 이미 rotate된 구 토큰을 사용하는 경우 → 탈취 의심
        // 대응: 기존 세션을 즉시 무효화하여 피해 최소화 (합법 사용자도 재로그인 필요)
        if (!storedToken.equals(command.refreshToken())) {
            log.warn("[refreshToken] 토큰 불일치 감지 - 재사용 공격 의심, 세션 무효화. userId: {}", user.getId());
            refreshTokenStore.delete(user.getId()); // 기존 세션 강제 종료
            throw new UserException(UserErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 5. Keycloak에 신규 토큰 발급 요청 (Keycloak이 기존 Refresh Token을 자동 무효화)
        TokenResult newTokens = keycloakAuthService.refreshToken(command.refreshToken());

        // 6. Redis에 신규 Refresh Token으로 교체 (원자적 덮어쓰기)
        refreshTokenStore.rotate(user.getId(), newTokens.refreshToken());

        // 7. 새로 발급된 토큰 반환
        return newTokens;
    }
}
