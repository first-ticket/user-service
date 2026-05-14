package com.firstticket.userservice.domain;

import com.firstticket.userservice.domain.exception.UserException;
import com.firstticket.userservice.domain.exception.UserErrorCode;

/**
 * 사용자 계정 상태 ENUM
 * - LOCKED : 로그인 시도 실패 5회시 적용 (TTL적용)
 *
 * 허용되는 상태 전이
 * - ACTIVE -> LOCKED (잠금)
 * - ACTIVE -> DELETED (삭제)
 * - LOCKED -> ACTIVE (잠금 해제)
 * - LOCKED -> DELETED
 * - DELETED -> (없음, 최종 상태)
 */
public enum UserStatus {

    ACTIVE {
        @Override
        public boolean canTransitionTo(UserStatus next) {
            // ACTIVE에서는 LOCKED or DELETED만 전이 가능
            return next == LOCKED || next == DELETED;
        }
    },

    LOCKED {
        @Override
        public boolean canTransitionTo(UserStatus next) {
            // LOCKED 에서는 ACTIVE(해제) 또는 DELETED(삭제)로만 이동 가능
            return next == ACTIVE || next == DELETED;
        }
    },

    DELETED {
        @Override
        public boolean canTransitionTo(UserStatus next) {
            // DELETED 는 되돌릴 수 없는 최종 상태 — 모든 전이 거부
            return false;
        }
    };

    /**
     * 목표 상태로의 전이 가능 여부 반환
     * 각 상수가 구체적인 허용 규칙을 재정의
     */
    public abstract boolean canTransitionTo(UserStatus next);

    /**
     * 전이 유효성 검증 메서드
     * 불가능한 전이라면 UserException(INVALID_STATUS_TRANSITION) 던짐
     * User Entity 의 lock(), unlock(), softDelete() 등 상태 변경 메서드에서 사용 및 호출
     */
    public void validateNext(UserStatus next) {
        if (!canTransitionTo(next)) {
            throw new UserException(UserErrorCode.INVALID_STATUS_TRANSITION);
        }
    }
}
