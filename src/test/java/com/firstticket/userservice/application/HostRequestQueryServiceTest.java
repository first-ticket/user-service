package com.firstticket.userservice.application;

import com.firstticket.userservice.application.dto.result.HostRequestResult;
import com.firstticket.userservice.domain.HostRequest;
import com.firstticket.userservice.domain.HostRequestStatus;
import com.firstticket.userservice.domain.query.HostRequestQueryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HostRequestQueryServiceTest {

    @InjectMocks
    private HostRequestQueryService hostRequestQueryService;

    @Mock
    private HostRequestQueryRepository hostRequestQueryRepository;

    @Nested
    @DisplayName("HOST 신청 목록 조회(getHostRequests)")
    class GetHostRequests {

        @Test
        @DisplayName("PENDING 상태 신청 목록을 HostRequestResult로 변환해 반환한다")
        void PENDING_목록_반환() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            HostRequest pending1 = HostRequest.create(UUID.randomUUID());
            HostRequest pending2 = HostRequest.create(UUID.randomUUID());
            Page<HostRequest> mockPage = new PageImpl<>(List.of(pending1, pending2), pageable, 2);

            // PENDING 상태로만 조회하는지 검증하기 위해 eq() 사용
            when(hostRequestQueryRepository.findAllByStatus(eq(HostRequestStatus.PENDING), any(Pageable.class)))
                .thenReturn(mockPage);

            // when
            Page<HostRequestResult> result = hostRequestQueryService.getHostRequests(pageable);

            // then
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent())
                .extracting(HostRequestResult::status)
                .containsOnly(HostRequestStatus.PENDING);
        }

        @Test
        @DisplayName("PENDING 신청이 없으면 빈 Page를 반환한다")
        void 빈_결과_반환() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            when(hostRequestQueryRepository.findAllByStatus(any(), any()))
                .thenReturn(Page.empty(pageable));

            // when
            Page<HostRequestResult> result = hostRequestQueryService.getHostRequests(pageable);

            // then
            assertThat(result.isEmpty()).isTrue();
            assertThat(result.getTotalElements()).isZero();
        }
    }
}
