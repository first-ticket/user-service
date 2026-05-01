package com.firstticket.userservice.application;

import com.firstticket.userservice.application.dto.result.HostRequestResult;
import com.firstticket.userservice.domain.HostRequest;
import com.firstticket.userservice.domain.HostRequestRepository;
import com.firstticket.userservice.domain.HostRequestStatus;
import com.firstticket.userservice.domain.User;
import com.firstticket.userservice.domain.UserRepository;
import com.firstticket.userservice.domain.UserRole;
import com.firstticket.userservice.domain.exception.UserErrorCode;
import com.firstticket.userservice.domain.exception.UserException;
import com.firstticket.userservice.domain.service.KeycloakAuthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 설계 결정 사항
 * - HostRequest는 User와 별개의 Aggregate이므로 별도의 Command Service로 처리
 * - HOST 신청에 대해 APPROVE 처리 시 User.changeRole(HOST) + Keycloak 동기화를 해당 서비스가 조율
 * - DDD - Aggregate 간 협력은 Application 계층이 담당
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HostRequestCommandService {

    private final HostRequestRepository hostRequestRepository;
    private final UserRepository userRepository;
    private final KeycloakAuthService keycloakAuthService;

    /**
     * HOST 신청 (CUSTOMER)
     *
     * 처리 흐름
     * 1. keycloakId로 신청자 User 조회 (X-User-Id 헤더 = Keycloak sub claim = keycloakId)
     * 2. CUSTOMER 역할 검증 (HOST/ADMIN은 신청 불가)
     * 3. PENDING 중복 신청 확인 (DB PK 기준)
     * 4. HostRequest 생성 (userId = user.getId() - DB PK)
     * 5. DB 저장 후 HostRequestResult 반환
     */
    @Transactional
    public HostRequestResult request(UUID keycloakId) {

        // 1. Keycloak ID로 User 조회 - X-User-Id는 JWT sub(= keycloakId)이므로 findByKeycloakId() 사용
        User user = userRepository.findByKeycloakId(keycloakId.toString())
            .orElseThrow(() -> {
                log.warn("[hostRequest] 존재하지 않는 사용자의 HOST 신청 시도 - keycloakId: {}", mask(keycloakId));
                return new UserException(UserErrorCode.USER_NOT_FOUND);
            });

        // 2. 역할 검증 - CUSTOMER만 HOST 신청 가능
        if (user.getRole() != UserRole.CUSTOMER) {
            log.warn("[hostRequest] 비CUSTOMER 역할의 HOST 신청 시도 - userId: {}, role: {}",
                mask(user.getId()), user.getRole());
            throw new UserException(UserErrorCode.HOST_REQUEST_ONLY_FOR_CUSTOMER);
        }

        // 3. PENDING 중복 신청 확인 - DB PK(user.getId())로 확인
        if (hostRequestRepository.existsByUserIdAndStatus(user.getId(), HostRequestStatus.PENDING)) {
            log.warn("[hostRequest] 중복 PENDING 신청 시도 - userId: {}", mask(user.getId()));
            throw new UserException(UserErrorCode.HOST_REQUEST_ALREADY_PENDING);
        }

        // 4. HostRequest 생성 - userId는 DB PK (host_requests.user_id FK)
        HostRequest hostRequest = HostRequest.create(user.getId());
        HostRequest saved = hostRequestRepository.save(hostRequest);

        log.info("[hostRequest] HOST 신청 완료 - userId: {}, requestId: {}",
            mask(user.getId()), mask(saved.getId()));
        return HostRequestResult.from(saved);
    }

    /**
     * HOST 신청 승인/거절 (ADMIN)
     *
     * 처리 흐름 - APPROVE
     * 1. HostRequest 조회
     * 2. HostRequest.approve() -> PENDING -> APPROVED status 전이
     * 3. User 조회 -> changeRole(HOST)
     * 4. Keycloak role 동기화 (CUSTOMER → HOST)
     *
     * 처리 흐름 - REJECT
     * 1. HostRequest 조회
     * 2. HostRequest.reject() → PENDING → REJECTED 전이 (불가 시 예외)
     *    User role은 변경하지 않습니다.
     */
    @Transactional
    public HostRequestResult approveOrReject(UUID requestId, HostRequestAction action, UUID adminId) {

        // 1. HostRequest 조회
        HostRequest hostRequest = hostRequestRepository.findById(requestId)
            .orElseThrow(() -> {
                log.warn("[approveOrReject] 존재하지 않는 HOST 신청 - requestId: {}", mask(requestId));
                return new UserException(UserErrorCode.HOST_REQUEST_NOT_FOUND);
            });

        if (action == HostRequestAction.APPROVE) {
            // 2. 상태 전이 - PENDING → APPROVED
            hostRequest.approve();

            // 3. 신청자 User 조회
            User user = userRepository.findById(hostRequest.getUserId())
                .orElseThrow(() -> {
                    // 데이터 정합성 오류 — 운영 추적을 위해 userId 원문 기록
                    log.error("[approveOrReject] 신청자 User 없음 - 데이터 정합성 오류. userId: {}",
                        hostRequest.getUserId());
                    return new UserException(UserErrorCode.USER_NOT_FOUND);
                });

            // 4. DB role 변경 (CUSTOMER → HOST) - User.changeRole() 내부에서 DELETED 불변식 검증
            UserRole oldRole = user.getRole();
            user.changeRole(UserRole.HOST);

            // 5. Keycloak role 동기화
            // Known limitation : DB 커밋 실패 시 Keycloak만 변경된 불일치 가능 -> 추후 Outbox 패턴 적용 예정
            keycloakAuthService.changeUserRole(user.getKeycloakId(), oldRole.name(), UserRole.HOST.name());

            log.info("[approveOrReject] HOST 승인 완료 - requestId: {}, userId: {}, adminId: {}",
                mask(requestId), mask(user.getId()), mask(adminId));

        } else {
            // REJECT - HostRequest 상태만 변경, User role 불변
            hostRequest.reject();
            log.info("[approveOrReject] HOST 거절 완료 - requestId: {}, adminId: {}",
                mask(requestId), mask(adminId));
        }

        return HostRequestResult.from(hostRequest);
    }

    /**
     * 운영 로그에서 UUID 식별자 부분 마스킹합니다.
     * 예) 637a8e5c-8f7c-41ff-b4bb-849755817a5b
     *  → 637a8e5c-****-****-****-************
     */
    private static String mask(UUID uuid) {
        if (uuid == null) return "null";
        return uuid.toString().substring(0, 8) + "-****-****-****-************";
    }
}
