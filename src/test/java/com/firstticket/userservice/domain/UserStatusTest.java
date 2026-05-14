package com.firstticket.userservice.domain;

import com.firstticket.userservice.domain.exception.UserErrorCode;
import com.firstticket.userservice.domain.exception.UserException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserStatusTest {

    @Nested
    @DisplayName("ACTIVE 상태 전이")
    class ActiveTransition {

        @Test
        @DisplayName("ACTIVE → LOCKED 전이는 허용된다")
        void active_to_locked_허용() {
            assertThat(UserStatus.ACTIVE.canTransitionTo(UserStatus.LOCKED)).isTrue();
        }

        @Test
        @DisplayName("ACTIVE → DELETED 전이는 허용된다")
        void active_to_deleted_허용() {
            assertThat(UserStatus.ACTIVE.canTransitionTo(UserStatus.DELETED)).isTrue();
        }

        @Test
        @DisplayName("ACTIVE → ACTIVE 전이는 허용되지 않아 INVALID_STATUS_TRANSITION 예외가 발생한다")
        void active_to_active_불허() {
            assertThatThrownBy(() -> UserStatus.ACTIVE.validateNext(UserStatus.ACTIVE))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    @Nested
    @DisplayName("LOCKED 상태 전이")
    class LockedTransition {

        @Test
        @DisplayName("LOCKED → ACTIVE 전이는 허용된다")
        void locked_to_active_허용() {
            assertThat(UserStatus.LOCKED.canTransitionTo(UserStatus.ACTIVE)).isTrue();
        }

        @Test
        @DisplayName("LOCKED → DELETED 전이는 허용된다")
        void locked_to_deleted_허용() {
            assertThat(UserStatus.LOCKED.canTransitionTo(UserStatus.DELETED)).isTrue();
        }

        @Test
        @DisplayName("LOCKED → LOCKED 전이는 허용되지 않아 INVALID_STATUS_TRANSITION 예외가 발생한다")
        void locked_to_locked_불허() {
            assertThatThrownBy(() -> UserStatus.LOCKED.validateNext(UserStatus.LOCKED))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    @Nested
    @DisplayName("DELETED 상태 전이")
    class DeletedTransition {

        @Test
        @DisplayName("DELETED는 최종 상태로 모든 전이가 허용되지 않는다")
        void deleted_모든_전이_불허() {
            // DELETED는 되돌릴 수 없는 최종 상태 — canTransitionTo()가 항상 false를 반환해야 함
            for (UserStatus next : UserStatus.values()) {
                assertThat(UserStatus.DELETED.canTransitionTo(next))
                    .as("DELETED → %s 전이는 허용되어서는 안 됨", next)
                    .isFalse();
            }
        }

        @Test
        @DisplayName("DELETED에서 validateNext() 호출 시 INVALID_STATUS_TRANSITION 예외가 발생한다")
        void deleted_validateNext_예외() {
            assertThatThrownBy(() -> UserStatus.DELETED.validateNext(UserStatus.ACTIVE))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_STATUS_TRANSITION);
        }
    }
}
