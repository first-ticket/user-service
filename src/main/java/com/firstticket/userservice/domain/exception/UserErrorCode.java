package com.firstticket.userservice.domain.exception;

import com.firstticket.common.response.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    // ===== User =====
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "이메일 형식이 올바르지 않습니다."),
    INVALID_PASSWORD_FORMAT(HttpStatus.BAD_REQUEST, "비밀번호는 8자 이상이어야 합니다."),
    INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "허용되지 않는 상태 전이입니다."),
    USER_ALREADY_DELETED(HttpStatus.GONE, "이미 삭제된 사용자입니다."),

    // 로그인 실패
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),

    // ===== HostRequest =====
    HOST_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "HOST 신청 요청을 찾을 수 없습니다."),
    INVALID_HOST_REQUEST_STATUS(HttpStatus.BAD_REQUEST, "허용되지 않은 HOST 신청 상태 전이입니다."),
    HOST_REQUEST_ALREADY_PENDING(HttpStatus.CONFLICT, "이미 검토 중인 HOST 신청이 존재합니다."),
    HOST_REQUEST_ONLY_FOR_CUSTOMER(HttpStatus.FORBIDDEN, "CUSTOMER 계정만 HOST 신청이 가능합니다."),

    // Token
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다."),

    // Keycloak
    ROLE_ASSIGN_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "keycloak 사용자 권한 설정에 실패했습니다."),
    KEYCLOAK_USER_DISABLE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Keycloak 사용자 비활성화에 실패했습니다."),
    KEYCLOAK_PASSWORD_CHANGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Keycloak 비밀번호 변경에 실패했습니다."),

    // 비밀번호 변경
    WRONG_CURRENT_PASSWORD(HttpStatus.UNPROCESSABLE_ENTITY, "현재 비밀번호가 올바르지 않습니다.");

    private final HttpStatus status;
    private final String message;
}
