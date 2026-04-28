package com.firstticket.userservice.application.dto.result;

/**
 * 로그인 성공 후 application 계층에서 토큰 결과 반환 DTO > presentation 계층으로
 *
 * 설계 결정 사항
 * - KeycloakAuthService(Port) 반환형으로도 사용되어 infrastructure 가 직접 Presentation DTO 를 생성하지 않도록 의도
 */
public record TokenResult(
    String accessToken, // Keycloak에서 발급한 AT
    String refreshToken // Keycloak에서 발급한 RT (Redis에 저장)
) {}
