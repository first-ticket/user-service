package com.firstticket.userservice.domain;

import com.firstticket.common.exception.BusinessException;
import com.firstticket.userservice.domain.exception.UserErrorCode;

import java.util.regex.Pattern;

/**
 * 사용자 - 이메일 주소를 표현하는 VO
 *
 * 설계 결정 사항
 * - VO는 가능한 record 로 선언 → 컴파일러가 equals/hashCode/toString 자동 생성, 불변 보장
 * - of() 정적 팩토리만 허용 → 생성 시점에 반드시 유효성 검증 수행 필요
 * - @Embeddable 대신 User Entity 가 String 컬럼 직접 소유 →
 *   Hibernate 6 record 임베딩 호환성 이슈 우회, JPA 설정 단순화
 * - @Valid는 Kafka/내부 호출 컨텍스트에서 동작하지 않으므로 VO 내부에서 직접 검증하는 것이 안전
 */
public record Email(String value) {

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /**
     * Email VO 의 유일한 생성 진입점
     * null or 형식이 맞지 않으면 BusinessException
     */
    public static Email of(String value) {
        validate(value); // 생성 전 검증 수행
        return new Email(value);
    }

    /**
     * 이메일 형식 검증 로직
     * null 체크 → 정규식 매칭 순서로 NPE 없이 검증
     */
    private static void validate(String value) {
        if (value == null || !EMAIL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(UserErrorCode.INVALID_EMAIL_FORMAT);
        }
    }
}
