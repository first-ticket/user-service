package com.firstticket.userservice.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.firstticket.userservice.domain.HostRequest;
import com.firstticket.userservice.domain.HostRequestStatus;

/**
 * HostRequest 전용 Spring Data JPA Repository
 */
public interface HostRequestJpaRepository extends JpaRepository<HostRequest, UUID> {

    /**
     * userId + status 조합으로 신청 단건 조회
     * 사용 예시 :
     *   findByUserIdAndStatus(userId, HostRequestStatus.PENDING) → 특정 사용자 ID의 처리 대기 신청 조회
     */
    Optional<HostRequest> findByUserIdAndStatus(UUID userId, HostRequestStatus status);

    /**
     * 특정 사용자의 특정 상태 신청 존재 여부
     * 중복 PENDING 신청 방지에 사용
     */
    boolean existsByUserIdAndStatus(UUID userId, HostRequestStatus status);

    // status 조건 + 페이지네이션 조회
    Page<HostRequest> findAllByStatus(HostRequestStatus status, Pageable pageable);
}
