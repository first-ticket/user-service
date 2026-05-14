package com.firstticket.userservice.presentation;

import com.firstticket.common.response.ApiResponse;
import com.firstticket.userservice.application.UserCommandService;
import com.firstticket.userservice.application.dto.result.UserResult;
import com.firstticket.userservice.presentation.dto.response.UserResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 로컬 개발 전용 컨트롤러
 *
 * 설계 결정 사항
 * - @Profile("local"): local 프로파일에서만 Spring 빈으로 등록됩니다.
 * - Gateway를 거치지 않고 user-service 포트로 직접 호출합니다.
 *   (X-User-Id 헤더 불필요, 인증 없이 접근)
 * - 경로: POST /api/v1/dev/seed
 */
@Slf4j
@Profile("local") // local 프로파일에서만 활성화
@RestController
@RequestMapping("/api/v1/dev")
@RequiredArgsConstructor
public class DevController {

    private final UserCommandService userCommandService;

    /**
     * 역할별 테스트 계정 생성 API
     * POST /api/v1/dev/seed
     *
     * 생성 계정:
     *   CUSTOMER - customer@first-ticket.com / Customer1234!
     *   HOST     - host@first-ticket.com     / Host1234!
     *   ADMIN    - admin@first-ticket.com    / Admin1234!
     *
     * 멱등성: 이미 존재하는 계정은 건너뛰고 기존 정보 반환
     * 호출 방법: POST http://localhost:{user-service-port}/api/v1/dev/seed
     */
    @PostMapping("/seed")
    public ResponseEntity<ApiResponse<List<UserResponse>>> seed() {
        log.info("[DevSeed] 테스트 계정 시드 요청");

        // 테스트 계정 3개 생성 (이미 존재하면 건너뜀)
        List<UserResult> results = userCommandService.seedTestUsers();

        // UserResult → UserResponse 변환 후 반환
        List<UserResponse> responses = results.stream()
            .map(UserResponse::from)
            .toList();

        return ApiResponse.success(UserSuccessCode.SEED_SUCCESS, responses);
    }
}
