package com.firstticket.userservice.domain;

import com.firstticket.common.exception.BusinessException;
import com.firstticket.userservice.domain.exception.UserErrorCode;

/**
 * 평문 비밀번호를 표현하는 VO
 *
 * 설계 결정 사항
 * - First Ticket 서비스의 비밀번호 해싱은 Keycloak Admin Client 가 담당
 * - 따라서 DB 에 비밀번호를 저장하지 않으므로 @Column 매핑 없음
 * - 해당 VO는 Keycloak으로 넘기기 전 최소한의 형식 검증을 담당합니다.
 * - User.create() 를 통해 Keycloak 에 계정을 생성할 때만 일시적으로 사용
 * - toString() 재정의 -> 로그/디버그 출력 시 평문 노출 방지 (보안사항)
 */
public record Password(String value) {

    // 비밀번호 길이 제한
    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 100;

    public Password {
        validate(value);
    }

    /**
     * Password VO 의 유일한 생성 진입점
     */
    public static Password of(String value) {
        return new Password(value);
    }

    /** null, 빈 문자열, 길이 제약 위반 검증 */
    private static void validate(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(UserErrorCode.INVALID_PASSWORD_FORMAT);
        }
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new BusinessException(UserErrorCode.INVALID_PASSWORD_FORMAT);
        }
    }

    /**
     * 평문이 로그에 출력되지 않도록 마스킹
     * Lombok @ToString 을 사용하지 않으므로 record 의 기본 toString 을 직접 재정의합니다.
     */
    @Override
    public String toString() {
        return "Password[***]";
    }
}
