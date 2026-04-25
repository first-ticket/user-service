package com.firstticket.userservice.domain;

import com.firstticket.common.exception.BusinessException;
import com.firstticket.userservice.domain.exception.UserErrorCode;

/**
 * HOST 신청 상태 ENUM
 * - 주최자는 ADMIN의 허가를 받아야만 HOST 권한 계정을 생성할 수 있습니다.
 *
 * 허용 상태 전이:
 *   PENDING  → APPROVED (승인 : UserRole 이 HOST 로 변경됨)
 *   PENDING  → REJECTED (거절)
 *   APPROVED → (없음, 최종 상태)
 *   REJECTED → (없음, 최종 상태)
 */

public enum HostRequestStatus {

    PENDING {
        @Override
        public boolean canTransitionTo(HostRequestStatus next) {
            // PENDING 에서만 APPROVED 또는 REJECTED 로 전이 가능
            return next == APPROVED || next == REJECTED;
        }
    },

    APPROVED {
        @Override
        public boolean canTransitionTo(HostRequestStatus next) {
            // 승인 완료 후 상태 변경 불가
            return false;
        }
    },

    REJECTED {
        @Override
        public boolean canTransitionTo(HostRequestStatus next) {
            // 거절 완료 후 상태 변경 불가
            return false;
        }
    };

    // 각 상수가 허용 전이 규칙을 직접 정의합니다
    public abstract boolean canTransitionTo(HostRequestStatus next);

    /**
     * 전이 가능 여부를 검증하고, 불가하면 BusinessException 예외를 던짐
     * HostRequest Entity 의 approve(), reject() 에서 사용, 호출
     */
    public void validateNext(HostRequestStatus next) {
        if (!canTransitionTo(next)) {
            throw new BusinessException(UserErrorCode.INVALID_HOST_REQUEST_STATUS);
        }
    }
}
