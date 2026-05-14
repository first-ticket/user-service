package com.firstticket.userservice.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.firstticket.userservice.application.dto.command.ChangePasswordCommand;
import com.firstticket.userservice.application.dto.command.LoginCommand;
import com.firstticket.userservice.application.dto.command.LogoutCommand;
import com.firstticket.userservice.application.dto.command.RefreshTokenCommand;
import com.firstticket.userservice.application.dto.command.SignupCommand;
import com.firstticket.userservice.application.dto.command.UpdateProfileCommand;
import com.firstticket.userservice.application.dto.result.TokenResult;
import com.firstticket.userservice.application.dto.result.UserResult;
import com.firstticket.userservice.domain.Email;
import com.firstticket.userservice.domain.Password;
import com.firstticket.userservice.domain.User;
import com.firstticket.userservice.domain.UserRepository;
import com.firstticket.userservice.domain.UserRole;
import com.firstticket.userservice.domain.UserStatus;
import com.firstticket.userservice.domain.exception.UserErrorCode;
import com.firstticket.userservice.domain.exception.UserException;
import com.firstticket.userservice.domain.service.AccessTokenBlacklist;
import com.firstticket.userservice.domain.service.KeycloakAuthService;
import com.firstticket.userservice.domain.service.RefreshTokenStore;
import com.firstticket.userservice.domain.service.RefreshTokenStore.RotateResult;

import com.firstticket.userservice.domain.service.AccessTokenCache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
    private final AccessTokenBlacklist accessTokenBlacklist;
    private final AccessTokenCache accessTokenCache;

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
        // 1. VO 생성 (VO 생성자에서 형식 검증 — 실패 시 UserException 발생 → 400)
        Email email = Email.of(command.email());
        Password password = Password.of(command.password()); // 검증 후 Keycloak에 전달하고 버림

        // 2. 이메일 중복 확인 (DB 레벨에서 우선 차단 — Keycloak 호출 전에 확인하여 불필요한 외부 호출 방지)
        if (userRepository.existsByEmail(email.value())) {
            throw new UserException(UserErrorCode.DUPLICATE_EMAIL);
        }

        // "CUSTOMER"를 명시적으로 전달 — roleName이 필요한 이유를 코드에서 드러냄
        String keycloakId = keycloakAuthService.createUser(
            email.value(),
            password.value(),
            "CUSTOMER"  // 일반 회원가입은 항상 CUSTOMER로 시작
        );

        // 4. User 엔티티 생성 (role: CUSTOMER 고정, status: ACTIVE 고정)
        User user = User.create(keycloakId, email, command.username());

        // Keycloak에서 받은 자신의 UUID를 created_by에 직접 주입
        user.initCreatedBy(UUID.fromString(keycloakId));

        // 5. DB 저장 후 Result DTO 반환
        User savedUser = userRepository.save(user);
        return UserResult.from(savedUser);
    }

    // ==================================== 테스트 계정 관련  =====================================
    // TO-DO : MVP 개발 이후 고도화 기간에 수정 예정

    /**
     * 로컬 개발 환경 전용 테스트 계정 시드 메서드
     * @Profile("local")에서만 호출됩니다.
     *
     * 설계 결정 사항
     * - CUSTOMER 이외의 role은 User.changeRole()로 변경
     *   (Keycloak createUser()가 이미 올바른 role을 할당하므로 DB 정합성을 맞추는 용도)
     */
    @Transactional
    public List<UserResult> seedTestUsers() {
        // 생성할 테스트 계정 명세
        List<SeedUserSpec> specs = List.of(
            new SeedUserSpec("customer@first-ticket.com", "Customer1234!", "테스트고객", UserRole.CUSTOMER),
            new SeedUserSpec("host@first-ticket.com", "Host1234!", "테스트호스트", UserRole.HOST),
            new SeedUserSpec("admin@first-ticket.com", "Admin1234!", "관리자", UserRole.ADMIN)
        );

        return specs.stream()
            .map(this::createOrGetSeedUser)
            .toList();
    }

    /**
     * 단일 테스트 계정 생성 또는 기존 계정 반환
     * - DB에 이미 존재하면 Keycloak 호출 없이 기존 계정 반환
     * - 없으면 Keycloak 생성 → DB INSERT
     */
    private UserResult createOrGetSeedUser(SeedUserSpec spec) {
        // 이미 DB에 존재하면 기존 계정 반환 (멱등성)
        return userRepository.findByEmail(spec.email())
            .map(existing -> {
                log.info("[seed] 이미 존재하는 계정 건너뜀 - email: {}, role: {}", spec.email(), spec.role());
                return UserResult.from(existing);
            })
            .orElseGet(() -> {
                String keycloakId = keycloakAuthService.createUser(
                    spec.email(),
                    spec.password(),
                    spec.role().name()
                );

                // User 엔티티 생성 — 기본 role은 CUSTOMER
                Email email = Email.of(spec.email());
                User user = User.create(keycloakId, email, spec.username());

                // CUSTOMER 이외의 역할은 changeRole()로 DB role 동기화
                if (spec.role() != UserRole.CUSTOMER) {
                    user.changeRole(spec.role());
                }

                user.initCreatedBy(UUID.fromString(keycloakId));

                User saved = userRepository.save(user);
                log.info("[seed] 테스트 계정 생성 완료 - email: {}, role: {}", spec.email(), spec.role());
                return UserResult.from(saved);
            });
    }

    /**
     * 부하테스트 전용 테스트 계정 시드 메서드
     * @Profile("load-test")에서만 호출됩니다.
     *
     * 설계 결정 사항
     * - 25명 모두 CUSTOMER role: 부하테스트 시나리오(로그인)는 역할 구분이 불필요
     * - IntStream으로 동적 생성: 계정 수 변경 시 상수만 수정하면 됨
     * - 이메일 패턴: testuser01~25@test.com (zero-padded → 정렬/가독성)
     * - 비밀번호 Test1234!: Password VO 검증 통과 조건(대소문자+숫자+특수문자)
     */
    @Transactional
    public List<UserResult> seedLoadTestUsers() {
        // 25명의 CUSTOMER 계정 명세를 동적 생성 (상수만 바꾸면 vuser 수 조정 가능)
        int userCount = 200;
        List<SeedUserSpec> specs = IntStream.rangeClosed(1, userCount)
            .mapToObj(i -> new SeedUserSpec(
                String.format("testuser%02d@test.com", i),
                "Test1234!",
                String.format("테스트유저%02d", i),
                UserRole.CUSTOMER
            ))
            .toList();

        return specs.stream()
            .map(this::createOrGetSeedUser) // 기존 멱등성 로직 재사용
            .toList();
    }

    /**
     * 테스트 계정 생성 명세 내부 DTO
     * seedTestUsers()와 createOrGetSeedUser()에서만 사용하므로 private으로 제한
     */
    private record SeedUserSpec(
        String email,
        String password,
        String username,
        UserRole role
    ) {
    }

    // ==================================== 테스트 계정 관련 끝 =====================================

    /**
     * 로그인
     * 처리 흐름
     * 1. 이메일로 DB에서 사용자 조회 (없으면 401)
     * 2. 계정 상태 검증 — ACTIVE 이외에는 로그인 거부
     * 3. AT 캐시 조회 → 히트 시 RefreshTokenStore에서 현재 RT를 조합하여 즉시 반환 (Keycloak 생략)
     * 4. (캐시 미스) Keycloak ROPC 호출 → Access Token + Refresh Token 발급
     * 5. Refresh Token Redis 저장
     * 6. Access Token 캐시 저장 (TTL은 JWT exp 기반으로 구현체가 계산)
     * 7. TokenResult 반환
     *
     * 설계 결정 사항
     * - AT 캐시에는 Access Token 문자열만 저장합니다. RT는 RefreshTokenStore가 Source of Truth
     *   Token Rotation 이후에도 RefreshTokenStore는 항상 최신 RT를 보유하므로 stale RT 반환 불가
     * - 캐시 히트 시 RT가 없는 경우(세션 만료/비정상 상태): AT 캐시를 삭제하고 Keycloak 경로로 폴백
     *   이론상 AT TTL(30분) < RT TTL(7일)이므로 정상 운영에서는 발생하지 않음
     */
    @Transactional(readOnly = true)
    public TokenResult login(LoginCommand command) {
        // 1. 이메일로 사용자 조회
        User user = userRepository.findByEmail(command.email())
            .orElseThrow(() -> {
                log.warn("[login] DB 조회 실패 - email: {}", command.email());
                return new UserException(UserErrorCode.INVALID_CREDENTIALS);
            });

        // 2. 계정 상태 검증: ACTIVE 이외에는 로그인 실패
        // LOCKED: 잠김 / DELETED: 탈퇴 처리
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("[login] 계정 비활성 상태 - userId: {}, status: {}", user.getId(), user.getStatus());
            throw new UserException(UserErrorCode.INVALID_CREDENTIALS);
        }

        // 3. AT 캐시 조회 - 동일 사용자의 재로그인 시 Keycloak ROPC 호출 생략 (성능 개선 핵심)
        Optional<String> cachedAt = accessTokenCache.find(command.email());
        if (cachedAt.isPresent()) {
            // AT 캐시 hit: RefreshTokenStore에서 현재 RT를 조합하여 반환
            // RefreshTokenStore는 Token Rotation을 통해 항상 최신 RT를 유지합니다.
            Optional<String> currentRt = refreshTokenStore.find(user.getId());
            if (currentRt.isPresent()) {
                // AT(캐시) + RT(최신) 조합 반환 — Keycloak 호출 없음 (~5ms)
                log.info("[login] AT 캐시 히트 - Keycloak 호출 생략, userId: {}", mask(user.getId()));
                return new TokenResult(cachedAt.get(), currentRt.get());
            }
            // RT가 없는 비정상 상태: stale 캐시 제거 후 Keycloak 경로로 폴백
            // (AT TTL=30분 < RT TTL=7일 이므로 정상 운영에서는 발생하지 않는 방어 코드)
            log.info("[login] AT 캐시 히트이나 RT 없음 — 캐시 무효화 후 Keycloak 재호출, userId: {}",
                mask(user.getId()));
            accessTokenCache.delete(command.email());
        }

        // 4. 캐시 miss - 기존과 동일하게 Keycloak Token Endpoint 호출
        TokenResult tokenResult = keycloakAuthService.login(command.email(), command.password());

        // 5. Refresh Token Redis 저장
        refreshTokenStore.save(user.getId(), tokenResult.refreshToken());

        // 6. Access Token 캐시 저장
        accessTokenCache.save(command.email(), tokenResult.accessToken());

        log.info("[login] 캐시 미스 - Keycloak 호출 후 캐시 저장, userId: {}", mask(user.getId()));
        // 7. AT + RT 반환
        return tokenResult;
    }

    /**
     * 로그아웃
     * 처리 흐름
     * 1. Gateway X-User-Id(keycloakId)로 사용자 조회
     * 2. Redis에서 Refresh Token 삭제 → 재발급 경로 차단
     * 3. Access Token을 blacklist에 등록 → 잔여 TTL 동안 즉시 무효화
     * 4. AT 캐시 무효화 → 로그아웃 후 stale 토큰이 재반환되는 경로 차단
     *
     * 설계 결정 사항
     * - jti 및 tokenExpEpoch는 Gateway AuthorizationHeaderFilter가 JWT에서 추출해 헤더로 주입
     *   user-service는 JWT 파싱 없이 헤더 값만 사용 (관심사 분리)
     * - tokenExpEpoch - now <= 0이면 이미 만료된 토큰 → blacklist 저장 생략 (AccessTokenBlacklistImpl 내부 처리)
     * - blacklist 조회는 Gateway가 Redis를 직접 읽으므로 서비스 간 호출 없음
     * - AT 캐시 무효화는 blacklist 등록과 함께 이중으로 수행 → 재로그인 시 stale 토큰 반환 방지
     */
    @Transactional(readOnly = true)
    public void logout(LogoutCommand command) {
        // 1. user.keycloakId로 사용자 조회
        User user = userRepository.findByKeycloakId(command.keycloakId())
            .orElseThrow(() -> {
                log.warn("[logout] 사용자 조회 실패 - keycloakId: {}", mask(command.keycloakId()));
                return new UserException(UserErrorCode.USER_NOT_FOUND);
            });

        // 2. Refresh Token 삭제
        refreshTokenStore.delete(user.getId());
        log.info("[logout] Refresh Token 삭제 완료 - userId: {}", mask(user.getId()));

        // 3. Access Token blacklist 등록
        // TTL = 토큰 만료 시각 - 현재 시각
        long ttlSeconds = command.tokenExpEpoch() - Instant.now().getEpochSecond();
        accessTokenBlacklist.add(command.jti(), ttlSeconds);
        log.info("[logout] Access Token blacklist 등록 완료 - userId: {}, ttl: {}s", mask(user.getId()), ttlSeconds);

        // 4. AT 캐시 무효화
        accessTokenCache.delete(user.getEmail());
        log.info("[logout] AT 캐시 무효화 완료 - userId: {}", mask(user.getId()));
    }

    /**
     * 토큰 재발급 (Token Rotation)
     * 처리 흐름
     * 1. Refresh Token JWT payload에서 sub(keycloakId) 추출
     *    ⚠서명 검증 없이 payload만 디코딩 - 사용자 식별용 라우팅 힌트로만 사용
     *    실제 토큰 유효성 검증은 Keycloak refreshToken() 호출에서 수행
     * 2. keycloakId로 사용자 조회 + ACTIVE 상태 확인
     * 3. Keycloak에 신규 토큰 발급 요청
     * 4. Redis Lua CAS rotate - oldToken 검증 + newToken 교체를 원자적으로 처리
     *    (동시 요청 시 하나만 성공, 불일치 시 세션 삭제 없이 예외만 반환)
     * 5. 새 토큰 반환
     */
    @Transactional(readOnly = true)
    public TokenResult refreshToken(RefreshTokenCommand command) {
        // 1. Refresh Token JWT payload에서 sub 추출 (서명 검증 없음 — 라우팅 힌트용)
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

        // 3. Keycloak 신규 토큰 발급 (Keycloak이 refreshToken 서명/만료 검증 담당)
        TokenResult newTokens = keycloakAuthService.refreshToken(command.refreshToken());

        // 4. Redis Lua CAS rotate - 원자적으로 oldToken 검증 + newToken 교체
        // 동시 요청이 두 개 들어와도 하나만 성공하도록 보장
        RotateResult rotateResult = refreshTokenStore.rotate(
            user.getId(),
            command.refreshToken(),   // oldToken
            newTokens.refreshToken()  // newToken
        );

        switch (rotateResult) {
            case NOT_FOUND -> {
                // 로그아웃 또는 TTL 만료 - 재로그인 필요
                log.warn("[refreshToken] Redis 토큰 없음 (로그아웃 or 만료) - userId: {}", user.getId());
                throw new UserException(UserErrorCode.INVALID_REFRESH_TOKEN);
            }
            case TOKEN_MISMATCH -> {
                // 이미 rotate된 구 토큰 사용 - 재사용 공격 의심
                log.warn("[refreshToken] 토큰 불일치 감지 - 재사용 공격 의심. userId: {}", user.getId());
                throw new UserException(UserErrorCode.INVALID_REFRESH_TOKEN);
            }
            case SUCCESS -> log.info("[refreshToken] 토큰 재발급 성공 - userId: {}", user.getId());
        }

        return newTokens;
    }

    // ADMIN 기능 - 사용자 Soft Delete (탈퇴 처리)
    @Transactional
    public void deleteUser(UUID adminId, UUID targetUserId) {
        // 1. PK로 사용자 조회
        User user = userRepository.findById(targetUserId)
            .orElseThrow(() -> {
                log.warn("[deleteUser] 존재하지 않는 사용자 - targetUserId: {}", targetUserId);
                return new UserException(UserErrorCode.USER_NOT_FOUND);
            });

        // 2. 이미 DELETED 상태인지 확인
        if (user.getStatus() == UserStatus.DELETED) {
            log.warn("[deleteUser] 이미 탈퇴된 사용자 재삭제 시도 - targetUserId: {}", targetUserId);
            throw new UserException(UserErrorCode.USER_ALREADY_DELETED);
        }

        // 3. DB Soft Delete: status=DELETED + deletedAt/deletedBy 기록
        // 트랜잭션 내이므로 아직 커밋되지 않은 상태
        user.softDelete(adminId);

        // 4. Keycloak 계정 비활성화
        // 실패 시 UserException 발생 → 트랜잭션 롤백 → DB softDelete도 취소
        keycloakAuthService.disableUser(user.getKeycloakId());

        log.info("[deleteUser] 사용자 탈퇴 처리 완료 - targetUserId: {}, adminId: {}", targetUserId, adminId);
    }

    // ADMIN 기능 - 사용자 역할 변경
    @Transactional
    public UserResult changeRole(UUID targetUserId, UserRole newRole) {
        // 1. PK로 사용자 조회 - 탈퇴 사용자 여부 검증은 User 도메인 엔티티에서 수행
        User user = userRepository.findById(targetUserId)
            .orElseThrow(() -> {
                log.warn("[changeRole] 존재하지 않는 사용자 - targetUserId: {}", targetUserId);
                return new UserException(UserErrorCode.USER_NOT_FOUND);
            });

        // 2. Keycloak role 변경 시 '기존 역할'이 필요하므로 changeRole() 호출 전에 캡처
        UserRole oldRole = user.getRole();

        // 3. DB role 변경 (트랜잭션 내, 아직 커밋 X)
        user.changeRole(newRole);

        // 4. Keycloak role 변경: 기존 역할 제거 → 새 역할 부여
        // 실패 시 UserException 발생 → 트랜잭션 롤백 → DB changeRole도 취소
        keycloakAuthService.changeUserRole(user.getKeycloakId(), oldRole.name(), newRole.name());

        log.info("[changeRole] 역할 변경 완료 - targetUserId: {}, {} → {}", targetUserId, oldRole, newRole);
        return UserResult.from(user);
    }

    /**
     * 내 정보 수정 (본인)
     * 처리 흐름
     * 1. keycloakId로 사용자 조회
     * 2. DELETED 상태 차단 (LOCKED는 수정 허용 - display name 변경은 ok)
     * 3. username 변경 후 UserResult 반환 (Keycloak 동기화 불필요 - user DB의 display name만 변경)
     */
    @Transactional
    public UserResult updateProfile(String keycloakId, UpdateProfileCommand command) {
        Objects.requireNonNull(keycloakId, "keycloakId는 null일 수 없습니다.");

        // 1. keycloakId로 사용자 조회
        User user = userRepository.findByKeycloakId(keycloakId)
            .orElseThrow(() -> {
                log.warn("[updateProfile] 존재하지 않는 사용자 - keycloakId: {}", mask(keycloakId));
                return new UserException(UserErrorCode.USER_NOT_FOUND);
            });

        // 2. 유효성 체크 후 수정 (엔티티 비즈니스 메서드로 이관)
        user.updateProfile(command.username());

        log.info("[updateProfile] 정보 수정 완료 - userId: {}", mask(user.getId()));
        return UserResult.from(user);
    }

    /**
     * 회원 탈퇴 (본인)
     * 처리 흐름
     * 1. keycloakId로 사용자 조회
     * 2. DB Soft Delete (status=DELETED, deletedBy=본인 DB PK)
     * 3. Keycloak 계정 비활성화 (로그인 차단)
     * 4. Redis Refresh Token 즉시 삭제 (현재 세션 무효화)
     * 5. AT 캐시 무효화 (탈퇴 사용자 토큰 즉시 무효화)
     */
    //TODO:DB 커밋 성공 후 Keycloak 호출 실패 시 불일치 발생 가능 -> MVP 구현 후 @TransactionalEventListener(AFTER_COMMIT) 또는 Outbox 패턴 적용 예정
    @Transactional
    public void withdraw(String keycloakId) {
        Objects.requireNonNull(keycloakId, "keycloakId는 null일 수 없습니다.");

        // 1. keycloakId로 사용자 조회 (이메일 획득을 위해 필요)
        User user = userRepository.findByKeycloakId(keycloakId)
            .orElseThrow(() -> {
                log.warn("[withdraw] 존재하지 않는 사용자 - keycloakId: {}", mask(keycloakId));
                return new UserException(UserErrorCode.USER_NOT_FOUND);
            });

        // 2. DB Soft Delete - user.softDelete() 내부에서 DELETED 상태 이중 전이 방지
        user.softDelete(user.getId());

        // 3. Keycloak 계정 비활성화 - 이후 로그인 불가
        // 실패 시 UserException 발생 -> @Transactional 롤백 -> DB softDelete 취소
        keycloakAuthService.disableUser(user.getKeycloakId());

        // 4. Redis Refresh Token 즉시 삭제 - 현재 세션 즉시 무효화
        refreshTokenStore.delete(user.getId());

        // 5. AT 캐시 무효화 - 탈퇴 사용자의 토큰이 재로그인 경로에서 반환되지 않도록 즉시 제거
        accessTokenCache.delete(user.getEmail());

        log.info("[withdraw] 회원탈퇴 완료 - userId: {}", mask(user.getId()));
    }

    /**
     * 비밀번호 변경 (본인)
     *
     * 처리 흐름
     * 1. keycloakId로 사용자 조회 (없으면 404)
     * 2. DELETED 상태 차단 (410)
     * 3. 새 비밀번호 형식 검증 — Password VO (8자 이상, 100자 이하)
     * 4. 현재 비밀번호 검증 — Keycloak ROPC 로그인 시도
     *    실패 시 WRONG_CURRENT_PASSWORD (401)
     * 5. Keycloak Admin API로 비밀번호 변경
     *
     * 설계 결정 사항
     * - 비밀번호는 Keycloak이 관리하므로 DB 변경 없음 → @Transactional(readOnly = true)
     * - 현재 비밀번호 검증에 keycloakAuthService.login()을 재사용합니다.
     *   별도 "비밀번호 확인" Keycloak API가 없으므로 ROPC 흐름이 가장 단순한 검증 수단입니다.
     * - login() 성공으로 발급된 토큰은 사용하지 않고 버립니다 (검증 목적으로만 호출).
     *
     * 추후 확장 포인트 (Notification Service 연동 시)
     * - 3번과 4번 사이에 이메일 인증 코드 검증 단계를 삽입합니다.
     *   이 메서드 시그니처와 흐름 구조는 유지됩니다.
     */
    @Transactional(readOnly = true) // DB 쓰기 없음 - Keycloak 전용 작업
    public void changePassword(String keycloakId, ChangePasswordCommand command) {
        Objects.requireNonNull(keycloakId, "keycloakId는 null일 수 없습니다.");

        // 1. keycloakId로 사용자 조회
        User user = userRepository.findByKeycloakId(keycloakId)
            .orElseThrow(() -> {
                log.warn("[changePassword] 존재하지 않는 사용자 - keycloakId: {}", mask(keycloakId));
                return new UserException(UserErrorCode.USER_NOT_FOUND);
            });

        // 2. 탈퇴 상태 사용자의 접근 차단
        if (user.getStatus() == UserStatus.DELETED) {
            log.warn("[changePassword] 탈퇴한 사용자 비밀번호 변경 시도 - userId: {}", mask(user.getId()));
            throw new UserException(UserErrorCode.USER_ALREADY_DELETED);
        }

        // 3. 새 비밀번호 형식 검증
        // 검증만 목적으로 생성하며 저장하지 않음 (비밀번호는 Keycloak이 관리)
        Password.of(command.newPassword());

        // TODO: verificationCodeStore.verify(user.getEmail(), command.verificationCode());
        // 이메일 인증코드 검사 로직 위치

        // 4. 현재 비밀번호 검증 - Keycloak ROPC로 실제 자격증명 확인
        // 검증 성공 후 발급된 세션은 즉시 revoke하여 고아 세션 누적을 방지
        try {
            TokenResult verificationToken =
                keycloakAuthService.login(user.getEmail(), command.currentPassword());
            // 검증 목적으로만 사용한 세션 즉시 revoke (비밀번호 변경과 무관하게 항상 수행)
            keycloakAuthService.revokeToken(verificationToken.refreshToken());
        } catch (UserException e) {
            if (e.getErrorCode() == UserErrorCode.INVALID_CREDENTIALS) {
                log.warn("[changePassword] 현재 비밀번호 불일치 - userId: {}", mask(user.getId()));
                throw new UserException(UserErrorCode.WRONG_CURRENT_PASSWORD);
            }
            log.error("[changePassword] 비밀번호 검증 중 예상치 못한 오류 - userId: {}, errorCode: {}",
                mask(user.getId()), e.getErrorCode());
            throw e;
        } catch (RuntimeException e) {
            log.error("[changePassword] 현재 비밀번호 검증 중 Keycloak 서버 오류 - userId: {}",
                mask(user.getId()), e);
            throw e;
        }

        // 5. Keycloak Admin API로 비밀번호 변경
        // 실패 시 KEYCLOAK_PASSWORD_CHANGE_FAILED (500) 발생
        keycloakAuthService.changePassword(user.getKeycloakId(), command.newPassword());

        // 6. AT 캐시 무효화 - 비밀번호 변경 후 구 토큰이 캐시에서 반환되는 경로 차단
        accessTokenCache.delete(user.getEmail());

        log.info("[changePassword] 비밀번호 변경 완료 - userId: {}", mask(user.getId()));
    }

    //UUID 마스킹
    private static String mask(UUID uuid) {
        if (uuid == null)
            return "null";
        return uuid.toString().substring(0, 8) + "-****-****-****-************";
    }

    // mask(String) 오버로드 - keycloakId는 String으로 전달되는 케이스 대응
    private static String mask(String id) {
        if (id == null || id.length() < 8)
            return "****";
        return id.substring(0, 8) + "-****-****-****-************";
    }
}
