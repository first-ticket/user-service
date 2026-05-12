package com.firstticket.userservice.infrastructure.init;

import com.firstticket.userservice.application.UserCommandService;
import com.firstticket.userservice.domain.service.KeycloakAuthService;
import com.firstticket.userservice.infrastructure.persistence.HostRequestJpaRepository;
import com.firstticket.userservice.infrastructure.persistence.UserJpaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 부하테스트 환경 전용 데이터 초기화 컴포넌트
 *
 * 설계 결정 사항
 * - @Profile("load-test"): prod/dev 등 기존 환경에 독립적인 별도 프로파일로 운영
 *   활성화 방법: -Dspring.profiles.active=prod,load-test
 * - LocalDataInitializer(@Profile("local"))와 목적이 다름:
 *   Local → 역할별 3종 계정 (개발/기능 테스트용)
 *   LoadTest → CUSTOMER 25명 (vuser 수만큼 동시 로그인 테스트용)
 * - 삭제 순서: host_requests → Keycloak → users (FK 제약 준수)
 * - Keycloak 삭제 실패는 무시하고 진행 (Keycloak과 DB 상태 불일치 허용)
 */
@Slf4j
@Component
@Profile("load-test") // prod 환경에서도 이 프로파일을 추가해야만 활성화됨
@RequiredArgsConstructor
public class LoadTestDataInitializer implements ApplicationRunner {

    private final HostRequestJpaRepository hostRequestJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final KeycloakAuthService keycloakAuthService;
    private final UserCommandService userCommandService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("[LoadTestDataInitializer] ===== 부하테스트 DB / Keycloak 초기화 시작 =====");

        // 1. host_requests 전체 삭제 (FK: host_requests.user_id → users.id)
        hostRequestJpaRepository.deleteAllInBatch();
        log.info("[LoadTestDataInitializer] host_requests 테이블 초기화 완료");

        // 2. 기존 users 조회 → Keycloak 사용자 완전 삭제
        // deleteUser()를 사용해야 동일 이메일로 재생성이 가능함 (disableUser는 이메일 재사용 불가)
        userJpaRepository.findAll().forEach(user -> {
            try {
                keycloakAuthService.deleteUser(user.getKeycloakId());
            } catch (Exception e) {
                // Keycloak 상태 불일치(이미 삭제됨 등)는 무시하고 계속 진행
                log.warn("[LoadTestDataInitializer] Keycloak 삭제 실패 (무시) - keycloakId: {}",
                    user.getKeycloakId());
            }
        });

        // 3. users 전체 삭제 (Keycloak 삭제 완료 후 수행)
        userJpaRepository.deleteAllInBatch();
        log.info("[LoadTestDataInitializer] users 테이블 초기화 완료");

        // 4. 부하테스트 계정 n명 생성 (testuser01~25@test.com, 전원 CUSTOMER)
        userCommandService.seedLoadTestUsers();
        log.info("[LoadTestDataInitializer] ===== 부하테스트 계정 seed 완료 (n명) =====");
    }
}
