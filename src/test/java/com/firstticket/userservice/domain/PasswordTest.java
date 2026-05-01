package com.firstticket.userservice.domain;

import com.firstticket.userservice.domain.exception.UserErrorCode;
import com.firstticket.userservice.domain.exception.UserException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordTest {

    @Nested
    @DisplayName("생성(of)")
    class Create {

        @Test
        @DisplayName("8자 이상 100자 이하 비밀번호는 정상 생성된다")
        void 유효한_비밀번호_생성() {
            Password password = Password.of("password1");

            assertThat(password.value()).isEqualTo("password1");
        }

        @Test
        @DisplayName("경계값 — 8자 비밀번호는 정상 생성된다")
        void 최소_길이_경계값_생성() {
            Password password = Password.of("12345678"); // 정확히 MIN_LENGTH

            assertThat(password.value()).hasSize(8);
        }

        @Test
        @DisplayName("경계값 — 100자 비밀번호는 정상 생성된다")
        void 최대_길이_경계값_생성() {
            Password password = Password.of("a".repeat(100)); // 정확히 MAX_LENGTH

            assertThat(password.value()).hasSize(100);
        }

        @Test
        @DisplayName("null 입력 시 INVALID_PASSWORD_FORMAT 예외가 발생한다")
        void null_입력_예외() {
            assertThatThrownBy(() -> Password.of(null))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_PASSWORD_FORMAT);
        }

        @Test
        @DisplayName("빈 문자열 입력 시 INVALID_PASSWORD_FORMAT 예외가 발생한다")
        void 빈_문자열_예외() {
            assertThatThrownBy(() -> Password.of(""))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_PASSWORD_FORMAT);
        }

        @Test
        @DisplayName("7자 미만 비밀번호는 INVALID_PASSWORD_FORMAT 예외가 발생한다")
        void 최소_길이_미만_예외() {
            assertThatThrownBy(() -> Password.of("1234567")) // MIN_LENGTH - 1
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_PASSWORD_FORMAT);
        }

        @Test
        @DisplayName("101자 초과 비밀번호는 INVALID_PASSWORD_FORMAT 예외가 발생한다")
        void 최대_길이_초과_예외() {
            assertThatThrownBy(() -> Password.of("a".repeat(101))) // MAX_LENGTH + 1
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_PASSWORD_FORMAT);
        }
    }

    @Nested
    @DisplayName("보안")
    class Security {

        @Test
        @DisplayName("toString()은 평문을 노출하지 않는다")
        void toString_마스킹() {
            // 로그/디버그 출력 시 비밀번호 원문이 노출되면 안 됨
            Password password = Password.of("mysecretpw");

            assertThat(password.toString())
                .isEqualTo("Password[***]")
                .doesNotContain("mysecretpw");
        }
    }
}
