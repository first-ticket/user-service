package com.firstticket.userservice.domain.query;

import com.firstticket.userservice.domain.HostRequest;
import com.firstticket.userservice.domain.HostRequestStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface HostRequestQueryRepository {

    Page<HostRequest> findAllByStatus(HostRequestStatus status, Pageable pageable);
}
