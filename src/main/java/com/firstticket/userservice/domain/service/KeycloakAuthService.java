package com.firstticket.userservice.domain.service;

import com.firstticket.userservice.application.dto.result.TokenResult;

/**
 * Keycloak 사용자 계정 관리 Port (외부 서비스 추상화)
 *
 * 설계 결정 사항
 * - domain 계층에 인터페이스(Port)를 선언하고 infrastructure 계층에서 구현(Adapter)
 * - domain 계층이 Keycloak Admin Client 라이브러리에 직접 의존하지 않도록 격리
 * - 비즈니스 로직이 외부 시스템(Keycloak)에 결합되지 않도록 했습니다.
 */
public interface KeycloakAuthService {

    // Keycloak Realm에 사용자를 생성하고 발급된 keycloakId(sub UUID)를 반환
    String createUser(String email, String plainPassword, String username);

    /**
     * Keycloak Token Endpoint로 email/password 자격증명을 전달하여
     * Access Token + Refresh Token 을 발급받습니다.
     */
    TokenResult login(String email, String password);

    TokenResult refreshToken(String refreshToken);

    /**
     * JWT 토큰 payload에서 sub(subject) 클레임 추출
     *
     * 설계 결정 사항
     * - PUBLIC 엔드포인트 (/token/refresh)에서는 Gateway X-User-Id 헤더가 없으므로
     * - Refresh Token JWT payload를 직접 디코딩하여 keycloakId를 추출합니다.
     * - 서명 검은은 이후 keycloak refreshToken() 호출 시 keycloak이 담당
     */
    String extractSubject(String jwtToken);
}
