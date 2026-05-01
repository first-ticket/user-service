package com.firstticket.userservice.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * HostRequest Aggregate 명령(Command) 전용 Repository 인터페이스
 * 구현체는 infrastructure/persistence/HostRequestRepositoryImpl
 */
public interface HostRequestRepository {

    // 저장 (CREATE & UPDATE)
    HostRequest save(HostRequest hostRequest);

    // PK로 조회
    Optional<HostRequest> findById(UUID id);

    /**
     * userId + status 조합으로 조회
     * 주요 사용처
     *   - HOST 요청에 대한 ADMIN의 승인/거절 처리 → findByUserIdAndStatus(userId, PENDING)
     *   - status 조건이 명확하므로 APPROVED 사용자에게 PENDING 신청이 반환되는 오류 방지
     */
    Optional<HostRequest> findByUserIdAndStatus(UUID userId, HostRequestStatus status);

    /**
     * 특정 사용자의 특정 상태 신청 존재 여부 확인
     * 주로 PENDING 중복 신청 방지에 사용
     *   existsByUserIdAndStatus(userId, HostRequestStatus.PENDING)
     */
    boolean existsByUserIdAndStatus(UUID userId, HostRequestStatus status);

    Page<HostRequest> findAllByStatus(HostRequestStatus status, Pageable pageable);
}
