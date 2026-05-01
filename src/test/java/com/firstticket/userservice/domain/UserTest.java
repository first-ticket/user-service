package com.firstticket.userservice.domain;

import com.firstticket.userservice.domain.exception.UserErrorCode;
import com.firstticket.userservice.domain.exception.UserException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    // 테스트 헬퍼 - ACTIVE 상태 User 생성
    private User activeUser() {
        return User.create("keycloak-test-id", Email.of("test@example.com"), "testUser");
    }

    @Nested
    @DisplayName("생성(create)")
    class Create {

        @Test
        @DisplayName("생성 시 초기 상태는 ACTIVE")
        void 생성_시_ACTIVE_상태() {
            User user = activeUser();

            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("생성 시 초기 역할은 CUSTOMER")
        void 생성_시_CUSTOMER_역할() {
            User user = activeUser();

            assertThat(user.getRole()).isEqualTo(UserRole.CUSTOMER);
        }

        @Test
        @DisplayName("생성 시 이메일은 Email VO 소문자 정규화 값으로 저장된다")
        void 생성_시_이메일_소문자_저장() {
            // Email.of()가 대문자를 소문자로 정규화하므로 User.email도 소문자여야 함
            User user = User.create("keycloak-id", Email.of("TEST@EXAMPLE.COM"), "testUser");

            assertThat(user.getEmail()).isEqualTo("test@example.com");
        }
    }

    @Nested
    @DisplayName("잠금(lock)")
    class Lock {

        @Test
        @DisplayName("ACTIVE 상태에서 lock() 호출 시 LOCKED로 전이된다")
        void active에서_lock_성공() {
            User user = activeUser();

            user.lock();

            assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        }

        @Test
        @DisplayName("이미 LOCKED 상태에서 lock() 호출 시 INVALID_STATUS_TRANSITION 예외가 발생한다")
        void locked에서_lock_예외() {
            User user = activeUser();
            user.lock(); // LOCKED 상태

            assertThatThrownBy(user::lock)
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_STATUS_TRANSITION);
        }

        @Test
        @DisplayName("DELETED 상태에서 lock() 호출 시 INVALID_STATUS_TRANSITION 예외가 발생한다")
        void deleted에서_lock_예외() {
            User user = activeUser();
            user.softDelete(UUID.randomUUID()); // DELETED 상태로 만들기

            assertThatThrownBy(user::lock)
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    @Nested
    @DisplayName("잠금 해제(unlock)")
    class Unlock {

        @Test
        @DisplayName("LOCKED 상태에서 unlock() 호출 시 ACTIVE로 전이된다")
        void locked에서_unlock_성공() {
            User user = activeUser();
            user.lock();

            user.unlock();

            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("ACTIVE 상태에서 unlock() 호출 시 INVALID_STATUS_TRANSITION 예외가 발생한다")
        void active에서_unlock_예외() {
            User user = activeUser();

            assertThatThrownBy(user::unlock)
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    @Nested
    @DisplayName("소프트 삭제(softDelete)")
    class SoftDelete {

        @Test
        @DisplayName("ACTIVE 상태에서 softDelete() 호출 시 DELETED로 전이된다")
        void active에서_softDelete_성공() {
            User user = activeUser();

            user.softDelete(UUID.randomUUID());

            assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        }

        @Test
        @DisplayName("LOCKED 상태에서도 softDelete() 호출 시 DELETED로 전이된다")
        void locked에서_softDelete_성공() {
            User user = activeUser();
            user.lock();

            user.softDelete(UUID.randomUUID());

            assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        }

        @Test
        @DisplayName("이미 DELETED 상태에서 softDelete() 호출 시 INVALID_STATUS_TRANSITION 예외가 발생한다")
        void deleted에서_softDelete_예외() {
            User user = activeUser();
            user.softDelete(UUID.randomUUID());

            assertThatThrownBy(() -> user.softDelete(UUID.randomUUID()))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    @Nested
    @DisplayName("역할 변경(changeRole)")
    class ChangeRole {

        @Test
        @DisplayName("ACTIVE 상태에서 역할을 HOST로 변경할 수 있다")
        void active에서_역할_변경_성공() {
            User user = activeUser();

            user.changeRole(UserRole.HOST);

            assertThat(user.getRole()).isEqualTo(UserRole.HOST);
        }

        @Test
        @DisplayName("DELETED 상태에서 역할 변경 시 USER_ALREADY_DELETED 예외가 발생한다")
        void deleted에서_역할_변경_예외() {
            User user = activeUser();
            user.softDelete(UUID.randomUUID());

            assertThatThrownBy(() -> user.changeRole(UserRole.HOST))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_ALREADY_DELETED);
        }
    }

    @Nested
    @DisplayName("프로필 수정(updateProfile)")
    class UpdateProfile {

        @Test
        @DisplayName("ACTIVE 상태에서 username을 변경할 수 있다")
        void active에서_프로필_수정_성공() {
            User user = activeUser();

            user.updateProfile("newUsername");

            assertThat(user.getUsername()).isEqualTo("newUsername");
        }

        @Test
        @DisplayName("DELETED 상태에서 프로필 수정 시 USER_ALREADY_DELETED 예외가 발생한다")
        void deleted에서_프로필_수정_예외() {
            User user = activeUser();
            user.softDelete(UUID.randomUUID());

            assertThatThrownBy(() -> user.updateProfile("newUsername"))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_ALREADY_DELETED);
        }
    }

    @Nested
    @DisplayName("createdBy 초기화(initCreatedBy)")
    class InitCreatedBy {

        @Test
        @DisplayName("null 전달 시 IllegalArgumentException이 발생한다")
        void null_전달_예외() {
            User user = activeUser();

            assertThatThrownBy(() -> user.initCreatedBy(null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("이미 초기화된 상태에서 재호출 시 IllegalStateException이 발생한다")
        void 재초기화_예외() {
            // 두 번째 호출에서 IllegalStateException이 발생해야 통과
            User user = activeUser();
            user.initCreatedBy(UUID.randomUUID());

            assertThatThrownBy(() -> user.initCreatedBy(UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
        }
    }
}
