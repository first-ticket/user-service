package com.firstticket.userservice.presentation;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.firstticket.common.response.ApiResponse;
import com.firstticket.common.web.AuthContext;
import com.firstticket.userservice.application.HostRequestCommandService;
import com.firstticket.userservice.application.UserCommandService;
import com.firstticket.userservice.application.UserQueryService;
import com.firstticket.userservice.application.dto.command.UpdateProfileCommand;
import com.firstticket.userservice.application.dto.result.HostRequestResult;
import com.firstticket.userservice.application.dto.result.UserResult;
import com.firstticket.userservice.presentation.dto.request.ChangePasswordRequest;
import com.firstticket.userservice.presentation.dto.request.UpdateProfileRequest;
import com.firstticket.userservice.presentation.dto.response.HostRequestResponse;
import com.firstticket.userservice.presentation.dto.response.UserResponse;

import jakarta.validation.Valid;
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
    private final UserCommandService userCommandService;
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
    public ResponseEntity<ApiResponse<UserResponse>> getMyInfo() {
        UUID keycloakId = AuthContext.getUserId();
        UserResult result = userQueryService.getMyInfo(keycloakId);
        return ApiResponse.success(UserSuccessCode.USER_FOUND, UserResponse.from(result));
    }

    /**
     * 내 정보 수정
     * PATCH /api/v1/users/me
     * Body: { "username": "새이름" }
     *
     * @return 200 OK + UserResponse (수정된 사용자 정보)
     *
     * 오류 응답:
     *   400 Bad Request  - username 누락 또는 50자 초과
     *   401 Unauthorized - X-User-Id 헤더 누락
     *   404 Not Found    - 사용자 없음
     *   410 Gone         - 이미 탈퇴된 사용자
     */
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
        @RequestBody @Valid UpdateProfileRequest request
    ) {
        UUID keycloakId = AuthContext.getUserId();
        UserResult result = userCommandService.updateProfile(
            keycloakId.toString(),
            new UpdateProfileCommand(request.username())
        );

        return ApiResponse.success(UserSuccessCode.PROFILE_UPDATED, UserResponse.from(result));
    }

    /**
     * 회원 탈퇴 (본인)
     * DELETE /api/v1/users/me
     *
     * 처리 결과: DB Soft Delete + Keycloak 비활성화 + Redis 세션 즉시 무효화
     *
     * @return 200 OK (탈퇴 완료 메시지, data 없음)
     *
     * 오류 응답:
     *   401 Unauthorized - X-User-Id 헤더 누락
     *   404 Not Found    - 사용자 없음
     *   410 Gone         - 이미 탈퇴된 사용자
     */
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> withdraw() {
        UUID keycloakId = AuthContext.getUserId();
        userCommandService.withdraw(keycloakId.toString());
        return ApiResponse.success(UserSuccessCode.WITHDRAW_SUCCESS);
    }

    /**
     * 본인 비밀번호 변경 API
     * POST /api/v1/users/me/password
     *
     * Gateway의 X-User-Id 헤더(keycloakId)로 본인을 식별합니다.
     * 현재 비밀번호 확인 후 새 비밀번호로 변경합니다.
     *
     * @param request currentPassword (현재 비밀번호), newPassword (새 비밀번호)
     * @return 200 OK (비밀번호 변경 완료)
     *
     * 오류 응답:
     *   400 Bad Request  — currentPassword/newPassword 빈 값 (@NotBlank 위반)
     *   400 Bad Request  — newPassword 8자 미만 (Password VO 검증 실패)
     *   401 Unauthorized — X-User-Id 헤더 누락 (Gateway 미인증 요청)
     *   422 Unprocessable Entity — currentPassword 불일치 (도메인 규칙 위반, WRONG_CURRENT_PASSWORD)
     *   404 Not Found    — 사용자 없음
     *   410 Gone         — 이미 탈퇴된 사용자
     *
     * 설계 결정 사항
     * - 추후 Notification Service 연동 시 이메일 인증 코드 검증 단계를 Service 계층에 추가합니다.
     */
    @PostMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
        @RequestBody @Valid ChangePasswordRequest request
    ) {
        // AuthContext에서 Gateway가 주입한 X-User-Id(keycloakId) 추출
        UUID keycloakId = AuthContext.getUserId();

        // Application 계층에 위임: 현재 비밀번호 검증 → Keycloak 비밀번호 변경
        userCommandService.changePassword(keycloakId.toString(), request.toCommand());

        return ApiResponse.success(UserSuccessCode.PASSWORD_CHANGED);
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
