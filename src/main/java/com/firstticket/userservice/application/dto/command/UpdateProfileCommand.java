package com.firstticket.userservice.application.dto.command;

/**
 * 내 정보 수정 Application Command DTO
 * 현재 수정 가능한 필드: username (display name)
 * 추후 필드 추가시 확장
 * 이메일·비밀번호 변경은 별도 API 로 분리
 */
public record UpdateProfileCommand(
    String username
) {}
