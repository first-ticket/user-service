package com.firstticket.userservice.application.dto.command;

import java.util.Objects;

/**
 * 설계 결정 사항
 * - currentPassword: 현재 비밀번호 (Keycloak ROPC 검증용)
 * - newPassword: 새 비밀번호 (Password VO 형식 검증 후 Keycloak Admin API 전달)
 *
 * 추후 확장 포인트
 * - Notification Service 연동 후 이메일 인증이 도입되면
 *   verificationCode 필드를 추가하고 Application 계층에서 검증 단계를 삽입
 */
public record ChangePasswordCommand(
    String currentPassword,
    String newPassword
) {
    // compact constructor - Application 계층 경계에서 null 유입 명시적 차단
    public ChangePasswordCommand {
        Objects.requireNonNull(currentPassword, "currentPassword는 null일 수 없습니다.");
        Objects.requireNonNull(newPassword, "newPassword는 null일 수 없습니다.");
    }
}
