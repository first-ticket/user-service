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

        // Keycloak Admin REST API: POST /admin/realms/{realm}/users
        Response response = keycloakAdminClient
            .realm(keycloakProperties.realm())
            .users()
            .create(user);

        // 409 Conflict: 이메일(username) 이미 Keycloak Realm 에 존재
        if (response.getStatus() == Response.Status.CONFLICT.getStatusCode()) {
            throw new UserException(UserErrorCode.DUPLICATE_EMAIL);
        }

        // 201 이외 응답: Keycloak 서버 오류 등 예기치 못한 실패
        if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {
            log.error("Keycloak 사용자 생성 실패 - status: {}, email: {}", response.getStatus(), email);
            throw new RuntimeException("Keycloak 사용자 생성에 실패했습니다. 상태 코드: " + response.getStatus());
        }

        // 생성 성공 시 Location 헤더에서 keycloakId 추출
        // Location: http://host/admin/realms/{realm}/users/{keycloakId}
        String location = response.getHeaderString("Location");
        if (location == null || !location.contains("/")) {
            log.error("Keycloak Location 헤더 파싱 실패 - location: {}", location);
            throw new RuntimeException("Keycloak 사용자 ID 추출에 실패했습니다.");
        }

        return location.substring(location.lastIndexOf('/') + 1); // 마지막 경로 세그먼트 = keycloakId
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
