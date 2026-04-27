package com.firstticket.userservice.infrastructure.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yaml의 keycloak.* 설정을 바인딩하는 Properties record
 *
 * application.yaml 키 매핑:
 *   keycloak.server-url -> serverUrl
 *   keycloak.realm -> realm
 *   keycloak.client-id -> clientId
 *   keycloak.client-secret -> clientSecret
 *   keycloak.admin-username -> adminUsername
 *   keycloak.admin-password -> adminPassword
 */
@ConfigurationProperties(prefix = "keycloak")
public record KeycloakProperties(
    String serverUrl,      // Keycloak 서버 URL (ex. http://localhost:8180)
    String realm,          // 서비스 Realm 이름 (ex. first-ticket)
    String clientId,       // 서비스 클라이언트 ID (user-service)
    String clientSecret,   // 클라이언트 시크릿
    String adminUsername,  // Admin REST API 호출용 계정
    String adminPassword,  // Admin REST API 호출용 비밀번호
    boolean emailVerified // Keycloak email verified 여부
) {}
