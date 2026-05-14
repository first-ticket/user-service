package com.firstticket.userservice.presentation.dto.request;

import com.firstticket.userservice.application.dto.command.LoginCommand;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

    @NotBlank(message = "이메일은 필수입니다.")
    String email,

    @NotBlank(message = "비밀번호는 필수입니다.")
    String password
) {

    /**
     * Presentation → Application 계층 변환용
     */
    public LoginCommand toCommand() {
        return new LoginCommand(email, password);
    }
}
