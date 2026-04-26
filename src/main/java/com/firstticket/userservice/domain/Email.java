package com.firstticket.userservice.domain;

import java.util.regex.Pattern;

import com.firstticket.userservice.domain.exception.UserErrorCode;
import com.firstticket.userservice.domain.exception.UserException;

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

    // static final 상수는 생성자보다 먼저 선언해야 IDE가 인식함

    // DB 컬럼 VARCHAR(255) 와 동일한 길이 제한 (방어코드)
    private static final int MAX_LENGTH = 255;

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    // compact canonical constructor
    // new Email(), of(), Jackson 역직렬화 등 모든 생성 경로에서 반드시 실행됨 → 검증 우회 불가능
    public Email {
        validate(value); // 생성 전에 검증
    }

    /**
     * Email VO 정적 팩토리 메서드
     * 실제 검증은 compact constructor 에서 수행되므로 생성만 담당
     */
    public static Email of(String value) {
        return new Email(value);
    }

    /**
     * 이메일 유효성 검증 로직
     * null 체크 → 길이 검증 → 정규식 매칭 순서로 검증 (NPE 방지)
     * 길이 초과 시 DB 예외 대신 도메인 레이어 예외로 사용자에게 정확한 메시지 전달
     */
    private static void validate(String value) {
        if (value == null
            || value.length() > MAX_LENGTH
            || !EMAIL_PATTERN.matcher(value).matches()) {
            throw new UserException(UserErrorCode.INVALID_EMAIL_FORMAT);
        }
    }
}
