package com.firstticket.userservice.application.dto.command;

public record SignupCommand(
    String email,
    String password,
    String username
) {}
