-- V2: host_requests 테이블 생성
-- HostRequest Aggregate 의 persistence 저장소
-- user_id 는 users.id 를 FK 로 참조하므로 V1 이후에 실행되어야 합니다. (V2로 작성)

CREATE TABLE host_requests
(
    id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,

    -- 신청한 사용자 UUID (FK - users.id)
    -- @ManyToOne JPA 매핑 대신 UUID 직접 참조, DB FK 는 유지하여 무결성 보호
    user_id UUID NOT NULL REFERENCES users (id),

    -- 신청 상태: PENDING / APPROVED / REJECTED (HostRequestStatus ENUM 과 매핑)
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    -- BaseEntity
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,

    -- BaseUserEntity
    created_by UUID NOT NULL,
    updated_by UUID,
    deleted_by UUID
);

-- (user_id, status) 복합 인덱스
-- PENDING 중복 신청 여부 확인(existsByUserIdAndStatus) 시 풀 스캔 방지
-- 특정 사용자의 신청 목록 조회 시 성능 향상
CREATE INDEX idx_host_requests_user_id_status ON host_requests (user_id, status);
