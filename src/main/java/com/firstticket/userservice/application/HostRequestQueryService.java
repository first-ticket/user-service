package com.firstticket.userservice.application;

import com.firstticket.userservice.application.dto.result.HostRequestResult;
import com.firstticket.userservice.domain.HostRequestRepository;
import com.firstticket.userservice.domain.HostRequestStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HostRequestQueryService {

    private final HostRequestRepository hostRequestRepository;

    /**
     * PENDING 상태의 HOST 신청 목록 페이지 조회
     * AdminController에서 Pageable(page, size, sort)을 전달
     */
    public Page<HostRequestResult> getHostRequests(Pageable pageable) {
        return hostRequestRepository.findAllByStatus(HostRequestStatus.PENDING, pageable)
            .map(HostRequestResult::from);
    }
}
