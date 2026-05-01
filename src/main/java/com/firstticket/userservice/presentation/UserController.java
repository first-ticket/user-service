package com.firstticket.userservice.presentation;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.firstticket.common.response.ApiResponse;
import com.firstticket.common.web.AuthContext;
import com.firstticket.userservice.application.HostRequestCommandService;
import com.firstticket.userservice.application.UserQueryService;
import com.firstticket.userservice.application.dto.result.HostRequestResult;
import com.firstticket.userservice.application.dto.result.UserResult;
import com.firstticket.userservice.presentation.dto.response.HostRequestResponse;
import com.firstticket.userservice.presentation.dto.response.UserResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 사용자 정보 컨트롤러
 * /api/v1/users 경로 — 사용자 리소스 CRUD 담당
 *
 * 설계 결정 사항
 * - AuthController(/api/v1/auth)와 UserController(/api/v1/users)를 분리한 이유:
 *   관심사(Concern) 분리 원칙 적용
 *     /auth  → 인증 플로우 (signup, login, logout, token/refresh)
 *     /users → 사용자 리소스 조회·수정 (getMyInfo, updateProfile, 관리자 목록 조회 등)
 *   향후 /users/{id}, /users(관리자 목록) 추가 시 같은 컨트롤러에 자연스럽게 확장 가능
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserQueryService userQueryService;
    private final HostRequestCommandService hostRequestCommandService;

    /**
     * 내 정보 조회 API
     * GET /api/v1/users/me
     *
     * Gateway의 AuthorizationHeaderFilter가 Bearer JWT를 검증하고
     * X-User-Id 헤더에 사용자 UUID를 주입합니다.
     * AuthContext가 내부적으로 헤더를 추출하므로 메서드 파라미터 선언이 불필요합니다.
     *
     * @return 200 OK + { id, email, username, role, status }
     *
     * 오류 응답:
     *   401 Unauthorized — X-User-Id 헤더 누락 또는 UUID 형식 오류 (Gateway 오동작)
     *   404 Not Found    — 사용자가 존재하지 않거나 DELETED 상태
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMyInfo(
    ) {
        UUID userId = AuthContext.getUserId();

        UserResult result = userQueryService.getMyInfo(userId);

        return ApiResponse.success(
            UserSuccessCode.USER_FOUND,
            UserResponse.from(result)
        );
    }

    /**
     * HOST 신청 API
     * POST /api/v1/users/me/host-requests
     *
     * Gateway의 X-User-Id 헤더로 신청자 UUID를 식별합니다.
     * 요청 바디 없음
     *
     * @return 201 Created + HostRequestResponse (id, userId, status: PENDING, createdAt)
     *
     * 오류 응답:
     *   401 Unauthorized - X-User-Id 헤더 누락
     *   409 Conflict     - 이미 PENDING 상태의 신청이 존재
     */
    @PostMapping("/me/host-requests")
    public ResponseEntity<ApiResponse<HostRequestResponse>> requestHost() {
        UUID keycloakId = AuthContext.getUserId();

        HostRequestResult result = hostRequestCommandService.request(keycloakId);

        return ApiResponse.success(
            UserSuccessCode.HOST_REQUEST_CREATED,
            HostRequestResponse.from(result)
        );
    }
}
