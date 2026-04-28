package com.firstticket.userservice.domain.service;

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
}
