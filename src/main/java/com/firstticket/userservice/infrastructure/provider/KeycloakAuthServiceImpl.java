package com.firstticket.userservice.infrastructure.provider;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firstticket.userservice.application.dto.result.TokenResult;
import com.firstticket.userservice.domain.exception.UserErrorCode;
import com.firstticket.userservice.domain.exception.UserException;
import com.firstticket.userservice.domain.service.KeycloakAuthService;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * domain 계층의 KeycloakAuthService (Port) 의 Adapter 구현체
 * Keycloak Admin Client를 사용하여 Realm에 사용자를 생성합니다.
 *
 * createUser: Keycloak Admin Client (Admin REST API) -> 사용자 생성
 * login: RestClient -> Token Endpoint (ROPC)
 * refreshToken: RestClient -> Token Endpoint (Refresh Token Grant)
 * extractSubject: JWT payload Base64 디코딩 -> sub 클레임 추출
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakAuthServiceImpl implements KeycloakAuthService {

    private final Keycloak keycloakAdminClient;
    private final RestClient keycloakRestClient;
    private final KeycloakProperties keycloakProperties;
    private final ObjectMapper objectMapper;

    @Override
    public String createUser(String email, String plainPassword, String roleName) {
        UserRepresentation user = buildUserRepresentation(email, plainPassword);

        try (Response response = keycloakAdminClient
            .realm(keycloakProperties.realm())
            .users()
            .create(user)) {

            // 409 Conflict -> 중복 이메일
            if (response.getStatus() == Response.Status.CONFLICT.getStatusCode()) {
                throw new UserException(UserErrorCode.DUPLICATE_EMAIL);
            }

            // keycloak 내부 오류
            if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {
                log.error("Keycloak 사용자 생성 실패 - status: {}", response.getStatus());
                throw new RuntimeException("Keycloak 사용자 생성에 실패했습니다. 상태 코드: " + response.getStatus());
            }

            // Location 헤더에서 사용자 UUID 추출
            String location = response.getHeaderString("Location");
            if (location == null || !location.contains("/")) {
                log.error("Keycloak Location 헤더 파싱 실패 - location: {}", location);
                throw new RuntimeException("Keycloak 사용자 ID 추출에 실패했습니다.");
            }

            String keycloakId = location.substring(location.lastIndexOf('/') + 1);

            // Location 헤더에서 추출한 값이 유효한 UUID인지 검증
            try {
                UUID.fromString(keycloakId);
            } catch (IllegalArgumentException e) {
                log.error("Keycloak 사용자 ID 형식 오류 - keycloakId가 UUID가 아닙니다.");
                throw new RuntimeException("Keycloak 사용자 ID 형식이 올바르지 않습니다.", e);
            }

            // roleName 파라미터로 역할 할당 (기존 "CUSTOMER" 하드코딩 제거)
            // signup()은 항상 "CUSTOMER"를 전달, 시드 API는 "HOST"/"ADMIN" 등을 전달
            try {
                assignRealmRole(keycloakId, roleName);
            } catch (UserException roleException) {
                // 보상 처리: 역할 부여 실패 시 생성된 Keycloak 사용자 정리
                try {
                    keycloakAdminClient
                        .realm(keycloakProperties.realm())
                        .users()
                        .get(keycloakId)
                        .remove();
                    log.warn("역할 부여 실패로 Keycloak 사용자 롤백 삭제 완료 - keycloakId: {}", keycloakId);
                } catch (Exception cleanupException) {
                    log.error("Keycloak 사용자 롤백 삭제 실패 - 수동 정리 필요 keycloakId: {}", keycloakId, cleanupException);
                    roleException.addSuppressed(cleanupException);
                }
                throw roleException;
            }

            return keycloakId;
        }
    }

    /**
     * Keycloak realm 역할을 사용자에게 할당
     *
     * @param keycloakId 대상 사용자의 Keycloak UUID
     * @param roleName   할당할 realm 역할 이름
     * @throws RuntimeException Keycloak realm에 해당 역할이 존재하지 않는 경우
     */
    private void assignRealmRole(String keycloakId, String roleName) {
        try {
            RoleRepresentation role = keycloakAdminClient
                .realm(keycloakProperties.realm())
                .roles()
                .get(roleName)
                .toRepresentation();

            keycloakAdminClient
                .realm(keycloakProperties.realm())
                .users()
                .get(keycloakId)
                .roles()
                .realmLevel()
                .add(List.of(role));

            log.info("Keycloak 역할 할당 완료 - keycloakId: {}, role: {}", keycloakId, roleName);

        } catch (Exception e) {
            // 인프라 예외를 도메인 예외로 변환 — 인프라 예외가 상위 계층까지 전파되지 않도록 차단
            log.error("Keycloak 역할 할당 실패 - keycloakId: {}, role: {}", keycloakId, roleName, e);
            throw new UserException(UserErrorCode.ROLE_ASSIGN_FAILED);
        }
    }

    private UserRepresentation buildUserRepresentation(String email, String plainPassword) {
        // 패스워드 자격증명 설정
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(plainPassword);
        credential.setTemporary(false);

        // 사용자 정보 설정
        UserRepresentation user = new UserRepresentation();
        user.setEmail(email);
        user.setEnabled(true);
        user.setEmailVerified(keycloakProperties.emailVerified());
        user.setCredentials(List.of(credential));

        return user;
    }

    @Override
    public TokenResult login(String email, String password) {
        // Token Endpoint URL : {serverUrl}/realms/{realm}/protocol/openid-connect/token
        String tokenUrl = buildTokenUrl();

        // OAuth2 Resource Owner Password Credentials Grant 요청 (ROPC)
        MultiValueMap<String, String> formBody = new LinkedMultiValueMap<>();
        formBody.add("grant_type", "password");
        formBody.add("client_id", keycloakProperties.clientId());
        formBody.add("client_secret", keycloakProperties.clientSecret());
        formBody.add("username", email);
        formBody.add("password", password); // 평문 패스워드 (TLS 보호)

        return requestToken(tokenUrl, formBody, UserErrorCode.INVALID_CREDENTIALS);
    }

    @Override
    public TokenResult refreshToken(String refreshToken) {
        // Token Endpoint URL (login과 동일한 엔드포인트, grant_type만 다름)
        String tokenUrl = buildTokenUrl();

        // OAuth2 Refresh Token Grant 요청
        MultiValueMap<String, String> formBody = new LinkedMultiValueMap<>();
        formBody.add("grant_type", "refresh_token");
        formBody.add("client_id", keycloakProperties.clientId());
        formBody.add("client_secret", keycloakProperties.clientSecret());
        formBody.add("refresh_token", refreshToken);

        return requestToken(tokenUrl, formBody, UserErrorCode.INVALID_REFRESH_TOKEN);
    }

    /**
     * Keycloak Token Endpoint 공통 호출 로직
     *
     * 설계 결정 사항
     * - login()과 refreshToken()은 동일한 Token Endpoint를 호출하므로 공통 메서드로 추출
     * - 4xx 오류 시 던질 에러 코드를 파라미터로 받아 호출자가 의미를 결정합니다.
     *   (login → INVALID_CREDENTIALS, refreshToken → INVALID_REFRESH_TOKEN)
     */
    private TokenResult requestToken(String tokenUrl, MultiValueMap<String, String> formBody,
        UserErrorCode credentialErrorCode) {
        try {
            // Content-Type: application/x-www-form-urlencoded로 Token Endpoint 호출
            KeycloakTokenResponse response = keycloakRestClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formBody)
                .retrieve()
                .onStatus(
                    status -> status.value() == 400 || status.value() == 401,
                    (req, res) -> {
                        log.warn("Keycloak 자격증명 오류 - status: {}", res.getStatusCode());
                        throw new UserException(credentialErrorCode);
                    }
                )
                // 5xx Server Error -> Keycloak 서버 장애
                .onStatus(
                    status -> status.is5xxServerError(),
                    (req, res) -> {
                        log.error("Keycloak 서버 오류 - status: {}", res.getStatusCode());
                        throw new RuntimeException("인증 서버에 일시적인 문제가 발생했습니다.");
                    }
                )
                .body(KeycloakTokenResponse.class); // access token, refresh token 역직렬화

            // 정상 응답이지만 body가 없는 비정상 케이스 방어
            if (response == null) {
                log.error("Keycloak Token Endpoint 응답 body가 null입니다.");
                throw new RuntimeException("Keycloak 토큰 발급 응답이 올바르지 않습니다.");
            }

            // null이나 빈 토큰이 Redis에 저장되면 재발급 흐름에서 무결성 오류 발생
            if (response.accessToken() == null || response.accessToken().isBlank()
                || response.refreshToken() == null || response.refreshToken().isBlank()) {
                log.error("Keycloak Token Endpoint 응답 토큰 필드 누락 - accessToken 존재:{}, refreshToken 존재:{}",
                    response.accessToken() != null, response.refreshToken() != null);
                throw new RuntimeException("Keycloak 토큰 발급 응답에 필수 토큰이 없습니다.");
            }

            // Port 반환형인 TokenResult로 변환 후 반환
            return new TokenResult(response.accessToken(), response.refreshToken());

        } catch (UserException e) {
            // 자격증명 오류는 위에서 처리, 그대로 던짐
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // 네트워크 오류, 타임아웃 등 checked 예외 -> 로깅 후 RuntimeException으로 전환
            log.error("Keycloak Token Endpoint 호출 중 오류 발생", e);
            throw new RuntimeException("Keycloak 토큰 발급 중 오류가 발생했습니다.", e);
        }
    }

    @Override
    public String extractSubject(String jwtToken) {
        // JWT 구조: {header}.{payload}.{signature} (각 파트는 Base64URL 인코딩)
        try {
            String[] parts = jwtToken.split("\\.");
            if (parts.length != 3) {
                // JWT 형식이 아닌 토큰 또는 변조된 토큰 필터링
                throw new UserException(UserErrorCode.INVALID_REFRESH_TOKEN);
            }

            // JWT Base64URL은 패딩(=) 없이 인코딩됨 → Java 디코더 호환을 위해 패딩 복원
            String paddedPayload = addPadding(parts[1]);
            byte[] payloadBytes = Base64.getUrlDecoder().decode(paddedPayload);

            // payload JSON에서 sub 클레임 추출
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = objectMapper.readValue(payloadBytes, Map.class);
            String sub = (String) claims.get("sub");

            if (sub == null || sub.isBlank()) {
                log.warn("Refresh Token sub 클레임이 존재하지 않습니다.");
                throw new UserException(UserErrorCode.INVALID_REFRESH_TOKEN);
            }
            return sub;
        } catch (UserException e) {
            throw e;
        } catch (Exception e) {
            // Base64 디코딩 실패, JSON 파싱 실패 등 -> 변조 또는 잘못된 형식의 토큰
            log.warn("Refresh Token sub 클레임 추출 실패 - 토큰 형식 오류", e);
            throw new UserException(UserErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    /**
     * Base64URL 문자열에 JWT 표준에서 생략된 패딩(=)을 복원합니다.
     * Java의 Base64.getUrlDecoder()는 패딩이 있어야 정상 동작하므로 필요합니다.
     */
    private String addPadding(String base64Url) {
        // base64url 인코딩된 문자열 길이는 4의 배수여야 함
        int remainder = base64Url.length() % 4;
        if (remainder == 2) return base64Url + "=="; // 2글자 부족
        if (remainder == 3) return base64Url + "=";  // 1글자 부족
        return base64Url;
    }

    /**
     * Keycloak Token Endpoint JSON 응답 역직렬화를 위한 내부 DTO
     * - 외부 노출이 불필요하므로 private 처리합니다.
     */
    private record KeycloakTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken
    ) {}

    /**
     * Keycloak Token Endpoint URL을 생성합니다.
     * login, refreshToken 두 메서드에서 동일하게 사용합니다.
     */
    private String buildTokenUrl() {
        return String.format(
            "%s/realms/%s/protocol/openid-connect/token",
            keycloakProperties.serverUrl(),
            keycloakProperties.realm()
        );
    }

    /**
     * Keycloak 사용자 계정 비활성화
     *
     * Admin Client로 UserRepresentation을 가져와 enabled = false 로 업데이트합니다.
     * Keycloak은 비활성화된 계정으로는 로그인/토큰 발급이 불가능합니다.
     *
     * @throws UserException KEYCLOAK_USER_DISABLE_FAILED - 비활성화 API 호출 실패 시
     */
    @Override
    public void disableUser(String keycloakId) {
        try {
            // 1. 현재 UserRepresentation 조회 (enabled 필드를 포함한 전체 객체가 필요)
            UserRepresentation userRepresentation = keycloakAdminClient
                .realm(keycloakProperties.realm())
                .users()
                .get(keycloakId)
                .toRepresentation();

            // 2. enabled 플래그를 false로 변경
            userRepresentation.setEnabled(false);

            // 3. Keycloak에 업데이트 요청 (PUT /admin/realms/{realm}/users/{id})
            keycloakAdminClient
                .realm(keycloakProperties.realm())
                .users()
                .get(keycloakId)
                .update(userRepresentation);

            log.info("[Keycloak] 사용자 비활성화 완료 - keycloakId: {}", keycloakId);

        } catch (Exception e) {
            // Admin Client 예외는 도메인 예외로 변환하여 상위 계층에 전파
            log.error("[Keycloak] 사용자 비활성화 실패 - keycloakId: {}", keycloakId, e);
            throw new UserException(UserErrorCode.KEYCLOAK_USER_DISABLE_FAILED);
        }
    }

    /**
     * Keycloak 사용자 Realm Role 변경
     */
    @Override
    public void changeUserRole(String keycloakId, String oldRoleName, String newRoleName) {
        // 보상 처리(롤백)에 재사용하기 위해 try 블록 바깥에 선언
        RoleRepresentation oldRole;

        try {
            // 1. 제거할 기존 역할 조회
            oldRole = keycloakAdminClient
                .realm(keycloakProperties.realm())
                .roles()
                .get(oldRoleName)
                .toRepresentation();

            // 2. 기존 역할 제거
            keycloakAdminClient
                .realm(keycloakProperties.realm())
                .users()
                .get(keycloakId)
                .roles()
                .realmLevel()
                .remove(List.of(oldRole));

            log.info("[Keycloak] 기존 역할 제거 완료 - keycloakId: {}, role: {}", keycloakId, oldRoleName);

        } catch (Exception e) {
            log.error("[Keycloak] 기존 역할 제거 실패 - keycloakId: {}, role: {}", keycloakId, oldRoleName, e);
            throw new UserException(UserErrorCode.ROLE_ASSIGN_FAILED);
        }

        try {
            // 3. 새 역할 부여
            assignRealmRole(keycloakId, newRoleName);

        } catch (UserException roleAssignException) {
            // 새 역할 부여 실패 → 기존 역할 복구 (보상 처리)
            // DB는 트랜잭션 롤백으로 복구되지만 Keycloak은 수동 복구 필요
            try {
                keycloakAdminClient
                    .realm(keycloakProperties.realm())
                    .users()
                    .get(keycloakId)
                    .roles()
                    .realmLevel()
                    .add(List.of(oldRole)); // 제거 단계에서 조회한 oldRole 재사용
                log.warn("[Keycloak] 역할 복구 완료 - keycloakId: {}, role: {}", keycloakId, oldRoleName);

            } catch (Exception rollbackException) {
                // 복구 과정 실패 -> Keycloak에 역할 없는 상태 -> 수동 정리 필요
                log.error("[Keycloak] 역할 복구 실패 - 수동 정리 필요 keycloakId: {}, role: {}", keycloakId, oldRoleName, rollbackException);
                roleAssignException.addSuppressed(rollbackException);
            }
            throw roleAssignException;
        }

        log.info("[Keycloak] 역할 변경 완료 - keycloakId: {}, {} → {}", keycloakId, oldRoleName, newRoleName);
    }

}
