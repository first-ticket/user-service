package com.firstticket.userservice.infrastructure.persistence;

import com.firstticket.userservice.domain.HostRequest;
import com.firstticket.userservice.domain.HostRequestStatus;
import com.firstticket.userservice.domain.query.HostRequestQueryRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class HostRequestQueryRepositoryImpl implements HostRequestQueryRepository {

    private final HostRequestJpaRepository hostRequestJpaRepository;

    @Override
    public Page<HostRequest> findAllByStatus(HostRequestStatus status, Pageable pageable) {
        return hostRequestJpaRepository.findAllByStatus(status, pageable);
    }
}
