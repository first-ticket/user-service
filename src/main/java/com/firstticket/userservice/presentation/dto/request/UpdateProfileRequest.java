package com.firstticket.userservice.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// PATCH /api/v1/users/me
public record UpdateProfileRequest(

    @NotBlank(message = "사용자 이름은 필수입니다.")
    @Size(max = 50, message = "사용자 이름은 50자 이하여야 합니다.")
    String username

) {}
