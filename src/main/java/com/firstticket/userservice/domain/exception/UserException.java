package com.firstticket.userservice.domain.exception;

import com.firstticket.common.exception.BusinessException;

/**
 * User 도메인 전용 예외클래스
 * BusinessException을 상속받아 도메인 계층에서 던지는 예외를 구분합니다
 * GlobalExceptionHandler가 BusinessException을 캐치하므로 별도 핸들러는 불필요합니다
 */

public class UserException extends BusinessException {

    // UserErrorCode를 받아서 부모 BusinessException에 위임
    public UserException(UserErrorCode errorCode) {
        super(errorCode);
    }
}
