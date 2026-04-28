package com.firstticket.userservice.presentation.dto.request;

import com.firstticket.userservice.application.dto.command.SignupCommand;
import jakarta.validation.constraints.NotBlank;

/**
 * 설계 결정 사항
 * - @NotBlank: null / 공백 문자열 조기 차단 → @Valid 적용 시 400 Bad Request (여기선 필수값 여부만 체크)
 *   실제 형식 검증(이메일 패턴, 비밀번호 길이)은 VO(Email, Password)가 담당
 * - @Email 등 Bean Validation 어노테이션 미사용:
 *   Kafka 또는 내부 호출 컨텍스트에서는 @Valid 가 동작하지 않으므로
 *   VO 내부 검증이 유일한 게이트 역할수행
 */
public record SignupRequest(
    @NotBlank(message = "이메일은 필수입니다.")
    String email,

    @NotBlank(message = "비밀번호는 필수입니다.")
    String password,

    @NotBlank(message = "사용자 이름은 필수입니다.")
    String username
) {

    /**
     * Presentation → Application 계층 변환
     * 계층 간 DTO 변환 책임을 Request DTO가 직접 담당합니다.
     */
    public SignupCommand toCommand() {
        return new SignupCommand(email, password, username);
    }
}
