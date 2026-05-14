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
 * 로컬 개발 환경 전용 데이터 초기화 컴포넌트
 *
 * 설계 결정 사항
 * - @Profile("local"): local 프로파일에서만 빈으로 등록되어 prod 환경에서는 실행되지 않습니다
 * - ApplicationRunner: Spring Context 초기화 완료 후 자동 실행됩니다.
 *   (user-service 재실행 시마다 DB + Keycloak 초기화 → seed 까지 자동 수행)
 * - 삭제 순서: host_requests 먼저 삭제 (FK → users 참조), 이후 users 삭제
 * - Keycloak 삭제 실패 시에도 예외를 무시하고 계속 진행합니다.
 *   (이미 삭제된 사용자거나 Keycloak 상태 불일치 시에도 초기화가 멈추지 않도록)
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalDataInitializer implements ApplicationRunner {

    private final HostRequestJpaRepository hostRequestJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final KeycloakAuthService keycloakAuthService;
    private final UserCommandService userCommandService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("[LocalDataInitializer] ===== 로컬 DB / Keycloak 초기화 시작 =====");

        // 1. host_requests 전체 삭제 (FK 제약으로 users보다 먼저 삭제)
        hostRequestJpaRepository.deleteAllInBatch();
        log.info("[LocalDataInitializer] host_requests 테이블 초기화 완료");

        // 2. users 전체 조회 후 Keycloak 사용자 삭제
        // disableUser()가 아닌 deleteUser()를 사용해야 동일 이메일로 seed 재실행이 가능합니다.
        userJpaRepository.findAll().forEach(user ->
            keycloakAuthService.deleteUser(user.getKeycloakId())
        );

        // 3. users 전체 삭제
        userJpaRepository.deleteAllInBatch();
        log.info("[LocalDataInitializer] users 테이블 초기화 완료");

        // 4. 테스트 계정 재생성 (CUSTOMER / HOST / ADMIN)
        userCommandService.seedTestUsers();
        log.info("[LocalDataInitializer] ===== 테스트 계정 seed 완료 =====");
    }
}
