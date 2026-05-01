package com.firstticket.userservice.infrastructure.persistence;

import com.firstticket.userservice.domain.HostRequest;
import com.firstticket.userservice.domain.HostRequestRepository;
import com.firstticket.userservice.domain.HostRequestStatus;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * HostRequestRepository 도메인 인터페이스의 JPA 구현체
 * HostRequestJpaRepository 에 실제 DB 작업을 위임
 */
@Repository
@RequiredArgsConstructor
public class HostRequestRepositoryImpl implements HostRequestRepository {

    private final HostRequestJpaRepository hostRequestJpaRepository;

    @Override
    public HostRequest save(HostRequest hostRequest) {
        return hostRequestJpaRepository.save(hostRequest);
    }

    @Override
    public Optional<HostRequest> findById(UUID id) {
        return hostRequestJpaRepository.findById(id);
    }

    @Override
    public Optional<HostRequest> findByUserIdAndStatus(UUID userId, HostRequestStatus status) {
        // status 조건이 포함된 조회
        return hostRequestJpaRepository.findByUserIdAndStatus(userId, status);
    }

    @Override
    public boolean existsByUserIdAndStatus(UUID userId, HostRequestStatus status) {
        return hostRequestJpaRepository.existsByUserIdAndStatus(userId, status);
    }

    @Override
    public Page<HostRequest> findAllByStatus(HostRequestStatus status, Pageable pageable) {
        // Spring Data가 COUNT 쿼리를 자동 생성해 Page 메타데이터(totalElements 등)를 채웁니다
        return hostRequestJpaRepository.findAllByStatus(status, pageable);
    }
}
