package com.firstticket.userservice.infrastructure.provider;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.firstticket.userservice.application.dto.result.TokenResult;
import com.firstticket.userservice.domain.exception.UserErrorCode;
import com.firstticket.userservice.domain.exception.UserException;
import com.firstticket.userservice.domain.service.KeycloakAuthService;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

/**
 * domain 계층의 KeycloakAuthService (Port) 의 Adapter 구현체
 * Keycloak Admin Client를 사용하여 Realm에 사용자를 생성합니다.
 *
 * createUser: Keycloak Admin Client (Admin REST API) -> 사용자 생성
 * login: RestClient -> Token Endpoint (ROPC)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakAuthServiceImpl implements KeycloakAuthService {

    private final Keycloak keycloakAdminClient;
    private final RestClient keycloakRestClient; // Token Endpoint용 HTTP 클라이언트
    private final KeycloakProperties keycloakProperties;

    @Override
    public String createUser(String email, String plainPassword, String username) {
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
            // 비정상 Keycloak 응답 시 다운스트림에서 원인 불명 예외가 발생하는 것을 방지
            try {
                UUID.fromString(keycloakId);
            } catch (IllegalArgumentException e) {
                log.error("Keycloak 사용자 ID 형식 오류 - keycloakId가 UUID가 아닙니다.");
                throw new RuntimeException("Keycloak 사용자 ID 형식이 올바르지 않습니다.", e);
            }

            return keycloakId;
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
        String tokenUrl = String.format(
            "%s/realms/%s/protocol/openid-connect/token",
            keycloakProperties.serverUrl(),
            keycloakProperties.realm()
        );

        // OAuth2 Resource Owner Password Credentials Grant 요청 (ROPC)
        MultiValueMap<String, String> formBody = new LinkedMultiValueMap<>();
        formBody.add("grant_type", "password");
        formBody.add("client_id", keycloakProperties.clientId());
        formBody.add("client_secret", keycloakProperties.clientSecret());
        formBody.add("username", email);
        formBody.add("password", password); // 평문 패스워드 (TLS 보호)

        try {
            // Content-Type: application/x-www-form-urlencoded로 Token Endpoint 호출
            KeycloakTokenResponse response = keycloakRestClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formBody)
                .retrieve()
                // 401 Unauthorized > 자격증명 불일치
                .onStatus(
                    status -> status.value() == 401,
                    (req, res) -> { throw new UserException(
                        UserErrorCode.INVALID_CREDENTIALS
                    );}
                )
                .body(KeycloakTokenResponse.class); // access token, refresh token 역직렬화

            if (response == null) {
                // 정상 응답이지만 body가 없는 비정상 케이스 방어
                log.error("Keycloak Token Endpoint 응답 body가 null입니다.");
                throw new RuntimeException("Keycloak 토큰 발급 응답이 올바르지 않습니다.");
            }

            // Port 반환형인 TokenResult로 변환 후 반환
            return new TokenResult(response.accessToken(), response.refreshToken());
        } catch (UserException e) {
            // 자격증명 오류는 위에서 처리, 그대로 던짐
            throw e;
        } catch (Exception e) {
            // 네트워크, 타임아웃 등 기타 예외 -> 로깅 후 RuntimeException으로
            log.error("Keycloak Token Endpoint 호출 중 오류 발생", e);
            throw new RuntimeException("Keycloak 토큰 발급 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * Keycloak Token Endpoint JSON 응답 역직렬화를 위한 내부 DTO
     * - 외부 노출이 불필요하므로 private 처리합니다.
     */
    private record KeycloakTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken
    ) {}
}
