package com.firstticket.userservice.presentation.dto.request;

import java.util.Objects;

import com.firstticket.userservice.application.dto.command.ChangePasswordCommand;
import jakarta.validation.constraints.NotBlank;

// 비밀번호 변경 요청 DTO (Presentation 계층)
public record ChangePasswordRequest(

    @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
    String currentPassword,  // 현재 사용 중인 비밀번호

    @NotBlank(message = "새 비밀번호를 입력해 주세요.")
    String newPassword       // 변경할 새 비밀번호

) {
    // compact constructor - @Valid 없이 직접 생성 시에도 null 유입 차단
    public ChangePasswordRequest {
        Objects.requireNonNull(currentPassword, "currentPassword는 null일 수 없습니다.");
        Objects.requireNonNull(newPassword, "newPassword는 null일 수 없습니다.");
    }

    // Presentation DTO → Application Command 변환
    public ChangePasswordCommand toCommand() {
        return new ChangePasswordCommand(currentPassword, newPassword);
    }
}
