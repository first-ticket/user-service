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

    /**
     * Keycloak Realm에 사용자를 생성하고 keycloakId(sub UUID)를 반환
     *
     * @param email        사용자 이메일 (Keycloak username으로도 사용)
     * @param plainPassword 평문 비밀번호 (Keycloak이 해싱 담당)
     * @param roleName     할당할 Realm Role 이름 (예: "CUSTOMER", "HOST", "ADMIN")
     *                     기존 username 파라미터는 Keycloak 생성 시 실제로 사용되지 않아
     *                     의미 있는 roleName으로 교체
     */
    String createUser(String email, String plainPassword, String roleName);

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

    /**
     * Keycloak 사용자 계정을 비활성화합니다. (탈퇴 처리 연동)
     * 탈퇴 처리시 DB와 같이 수행되어야 합니다 (동일 트랜잭션)
     *
     * 설계 결정 사항
     * - DB softDelete와 동일한 트랜잭션 내에서 호출됩니다.
     * - 실패 시 예외를 던져 트랜잭션을 롤백시키고 DB-Keycloak 정합성을 유지합니다.
     */
    void disableUser(String keycloakId);

    /**
     * Keycloak 사용자의 Realm Role을 변경합니다.
     * DB와 같이 수행되어야 합니다 (동일 트랜잭션)
     *
     * 설계 결정 사항
     * - Keycloak은 역할교체 API가 내부적으로 없으므로, 기존 역할 제거 -> 새 역할 부여 순서로 처리합니다.
     * - 기존 역할 제거 실패 시 예외를 던집니다. (새 역할 부여 단계로 진입하지 않음)
     * - DB changeRole과 동일한 트랜잭션 내에서 호출되므로 실패 시 DB도 롤백됩니다.
     */
    void changeUserRole(String keycloakId, String oldRoleName, String newRoleName);

    /**
     * Keycloak 사용자 계정을 완전히 삭제합니다. (로컬 테스트시 초기화 전용)
     *
     * 설계 결정 사항
     * - disableUser()는 비활성화만 시키므로 동일 이메일로 재가입이 불가합니다.
     * - 로컬 환경 초기화 시에는 Keycloak 사용자를 완전 삭제해야 테스트 계정 생성 api 재실행이 가능합니다.
     * - 운영 환경에서는 이 메서드를 호출하지 않습니다. (@Profile("local") 컴포넌트 전용)
     */
    void deleteUser(String keycloakId);

    /**
     * Keycloak 사용자 비밀번호를 변경합니다.
     *
     * 설계 결정 사항
     * - Keycloak Admin Client의 resetPassword() API를 사용합니다.
     *   (POST /admin/realms/{realm}/users/{id}/reset-password)
     *
     * 추후 확장
     * - Notification Service 구현 시 이메일 통한 인증 코드 검증을 호출부(Application 계층)에서
     *   이 메서드 호출 전에 추가
     *
     * @param keycloakId  비밀번호를 변경할 사용자의 Keycloak UUID
     * @param newPassword 새 비밀번호 (평문, Keycloak이 해싱 담당)
     */
    void changePassword(String keycloakId, String newPassword);

    /**
     * 검증 목적으로 발급된 Keycloak 세션을 즉시 revoke합니다.
     * revoke해주지 않으면 반복 호출시 계속 세션이 쌓이는 현상 발견 > 별도 revoke 호출로 처리
     * Token Revocation Endpoint: POST /realms/{realm}/protocol/openid-connect/revoke
     *
     * @param refreshToken revoke할 refresh token
     */
    void revokeToken(String refreshToken);
}
