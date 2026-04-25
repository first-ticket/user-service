package com.firstticket.userservice.domain.exception;

import com.firstticket.common.response.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * User / HostRequest 도메인 전용 에러 코드 ENUM
 * common 모듈의 ErrorCode 인터페이스를 구현
 * GlobalExceptionHandler 에서 일관된 ApiResponse 형태로 처리
 *
 * 코드 넘버링 구분
 *   U-001~U-099 : User 관련
 *   U-101~U-199 : HostRequest 관련
 */
@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    // ===== User =====
    USER_NOT_FOUND("U-001", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    DUPLICATE_EMAIL("U-002", "이미 사용 중인 이메일입니다.", HttpStatus.CONFLICT),
    INVALID_EMAIL_FORMAT("U-003", "이메일 형식이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD_FORMAT("U-004", "비밀번호는 8자 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
    INVALID_STATUS_TRANSITION("U-005", "허용되지 않는 상태 전이입니다.", HttpStatus.BAD_REQUEST),
    USER_ALREADY_DELETED("U-006", "이미 삭제된 사용자입니다.", HttpStatus.GONE),

    // ===== HostRequest =====
    HOST_REQUEST_NOT_FOUND("U-101", "HOST 신청 요청을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    INVALID_HOST_REQUEST_STATUS("U-102", "허용되지 않은 HOST 신청 상태 전이입니다.", HttpStatus.BAD_REQUEST),
    HOST_REQUEST_ALREADY_PENDING("U-103", "이미 검토 중인 HOST 신청이 존재합니다.", HttpStatus.CONFLICT);

    private final String code; // API 응답에 포함되는 서비스 내부 식별 코드
    private final String message; // 오류 메시지
    private final HttpStatus httpStatus; // HTTP 응답 상태코드

    /** ErrorCode 인터페이스 필수 구현 사항 - HttpStatus 반환 */
    @Override
    public HttpStatus getStatus() {
        return this.httpStatus;
    }
}
