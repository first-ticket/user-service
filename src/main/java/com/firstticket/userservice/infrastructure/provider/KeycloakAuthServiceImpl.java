package com.firstticket.userservice.infrastructure.provider;

import com.firstticket.userservice.domain.exception.UserErrorCode;
import com.firstticket.userservice.domain.exception.UserException;
import com.firstticket.userservice.domain.service.KeycloakAuthService;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * domain 계층의 KeycloakAuthService (Port) 의 Adapter 구현체
 * Keycloak Admin Client를 사용하여 Realm에 사용자를 생성합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakAuthServiceImpl implements KeycloakAuthService {

    private final Keycloak keycloakAdminClient;
    private final KeycloakProperties keycloakProperties;

    @Override
    public String createUser(String email, String plainPassword, String username) {
        UserRepresentation user = buildUserRepresentation(email, plainPassword);

        try (Response response = keycloakAdminClient
            .realm(keycloakProperties.realm())
            .users()
            .create(user)) {

            if (response.getStatus() == Response.Status.CONFLICT.getStatusCode()) {
                throw new UserException(UserErrorCode.DUPLICATE_EMAIL);
            }

            if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {
                log.error("Keycloak 사용자 생성 실패 - status: {}", response.getStatus());
                throw new RuntimeException("Keycloak 사용자 생성에 실패했습니다. 상태 코드: " + response.getStatus());
            }

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
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(plainPassword);
        credential.setTemporary(false);

        UserRepresentation user = new UserRepresentation();
        user.setEmail(email);
        user.setEnabled(true);
        user.setEmailVerified(keycloakProperties.emailVerified());
        user.setCredentials(List.of(credential));

        return user;
    }
}
