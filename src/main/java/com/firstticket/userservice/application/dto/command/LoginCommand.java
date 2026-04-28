package com.firstticket.userservice.application.dto.command;

/**
 * 설계 결정 사항
 * - 로그인시 형식 검증은 회원가입 시와 다르게 Email VO 를 의도적으로 사용하지 않습니다.
 * - 로그인 실패(잘못된 자격증명) 및 형식 오류를 동일한 401 로 처리하면 UX 및 보안에 좋지 못함
 * - 따라서 @NotBlank(400) 수준의 필수값 검증만 Presentation 계층에서 처리하고
 * - 실제 인증은 Keycloak에 위임하는 형태로 구성
 */
public record LoginCommand(
    String email,
    String password
) {}
