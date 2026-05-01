package com.firstticket.userservice.domain;

import com.firstticket.userservice.domain.exception.UserErrorCode;
import com.firstticket.userservice.domain.exception.UserException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTest {

    @Nested
    @DisplayName("생성(of)")
    class Create {

        @Test
        @DisplayName("유효한 이메일은 정상 생성된다")
        void 유효한_이메일_생성() {
            Email email = Email.of("user@example.com");

            assertThat(email.value()).isEqualTo("user@example.com");
        }

        @Test
        @DisplayName("대문자 이메일은 소문자로 정규화된다")
        void 대문자_소문자_정규화() {
            // compact constructor 내부에서 toLowerCase(Locale.ROOT) 수행
            Email email = Email.of("USER@EXAMPLE.COM");

            assertThat(email.value()).isEqualTo("user@example.com");
        }

        @Test
        @DisplayName("null 입력 시 INVALID_EMAIL_FORMAT 예외가 발생한다")
        void null_입력_예외() {
            assertThatThrownBy(() -> Email.of(null))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_EMAIL_FORMAT);
        }

        @Test
        @DisplayName("빈 문자열 입력 시 INVALID_EMAIL_FORMAT 예외가 발생한다")
        void 빈_문자열_예외() {
            assertThatThrownBy(() -> Email.of(""))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_EMAIL_FORMAT);
        }

        @Test
        @DisplayName("공백만 있는 입력 시 INVALID_EMAIL_FORMAT 예외가 발생한다")
        void 공백_입력_예외() {
            assertThatThrownBy(() -> Email.of("   "))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_EMAIL_FORMAT);
        }

        @Test
        @DisplayName("@ 없는 문자열은 INVALID_EMAIL_FORMAT 예외가 발생한다")
        void at_없는_형식_예외() {
            assertThatThrownBy(() -> Email.of("invalidemail"))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_EMAIL_FORMAT);
        }

        @Test
        @DisplayName("도메인 없는 이메일은 INVALID_EMAIL_FORMAT 예외가 발생한다")
        void 도메인_없는_이메일_예외() {
            assertThatThrownBy(() -> Email.of("user@"))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_EMAIL_FORMAT);
        }

        @Test
        @DisplayName("255자 초과 이메일은 INVALID_EMAIL_FORMAT 예외가 발생한다")
        void 길이_초과_예외() {
            // "a" * 250 + "@b.com" = 256자 → MAX_LENGTH(255) 초과
            String longEmail = "a".repeat(250) + "@b.com";

            assertThatThrownBy(() -> Email.of(longEmail))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_EMAIL_FORMAT);
        }
    }
}
