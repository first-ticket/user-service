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
    LOGIN_SUCCESS(HttpStatus.OK, "로그인 성공"), // 200
    TOKEN_REFRESHED(HttpStatus.OK, "토큰이 재발급되었습니다."), // 200
    USER_FOUND(HttpStatus.OK, "사용자 정보를 조회했습니다."), // 200
    SEED_SUCCESS(HttpStatus.CREATED, "테스트 계정이 생성되었습니다."), // 201

    //ADMIN CRUD
    USER_LIST_FOUND(HttpStatus.OK, "사용자 목록을 조회했습니다."), // 200
    USER_DELETED(HttpStatus.OK, "사용자 탈퇴 처리가 완료되었습니다."), // 200
    ROLE_CHANGED(HttpStatus.OK, "사용자 역할이 변경되었습니다."); // 200

    private final HttpStatus status;
    private final String message;
}
