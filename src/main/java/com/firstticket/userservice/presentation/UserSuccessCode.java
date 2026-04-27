package com.firstticket.userservice.presentation;

import com.firstticket.common.response.SuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * User 도메인 성공 응답 코드
 * common 모듈의 SuccessCode 인터페이스를 구현
 */
@Getter
@RequiredArgsConstructor
public enum UserSuccessCode implements SuccessCode {

    USER_CREATED(HttpStatus.CREATED, "회원가입이 완료되었습니다."), // 201
    USER_FOUND(HttpStatus.OK, "사용자 정보를 조회했습니다.");       // 200

    private final HttpStatus status;
    private final String message;
}
