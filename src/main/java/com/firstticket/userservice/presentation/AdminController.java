package com.firstticket.userservice.presentation;

import com.firstticket.common.exception.BusinessException;
import com.firstticket.common.response.ApiResponse;
import com.firstticket.common.response.CommonErrorCode;
import com.firstticket.common.web.AuthContext;
import com.firstticket.common.web.UserRole;
import com.firstticket.userservice.application.UserCommandService;
import com.firstticket.userservice.application.UserQueryService;
import com.firstticket.userservice.application.dto.result.UserResult;
import com.firstticket.userservice.domain.query.UserSummaryData;
import com.firstticket.userservice.presentation.dto.request.ChangeRoleRequest;
import com.firstticket.userservice.presentation.dto.request.UserSearchRequest;
import com.firstticket.userservice.presentation.dto.response.AdminUserResponse;
import com.firstticket.userservice.presentation.dto.response.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * ADMIN 전용 사용자 관리 컨트롤러

 * 설계 결정 사항
 * - @RequestMapping을 클래스 레벨에 선언하지 않는 이유:
 *   /admin/users와 /users/{id}/role 두 개의 다른 URL을 취급하므로
 *   메서드별로 전체 경로를 명시합니다.
 *
 * - ADMIN 권한 검증:
 *   Gateway가 주입한 X-User-Role 헤더를 AuthContext를 통해 확인합니다.
 *   Spring Security가 없으므로 requireAdmin()을 각 메서드 첫 줄에서 호출합니다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class AdminController {

    private final UserQueryService userQueryService;
    private final UserCommandService userCommandService;

    /**
     * 사용자 목록 조회 (동적 검색 + 페이지네이션)
     * GET /api/v1/admin/users?email=&username=&role=&status=&page=0&size=10
     *
     * @param request  이메일·이름·역할·상태 검색 조건 (모두 Optional)
     * @param pageable 페이지 정보 (size는 10/30/50만 허용, 그 외 → 10으로 강제)
     * @return 200 OK + Page<AdminUserResponse>
     *
     * 오류 응답
     * - 401 Unauthorized — X-User-Role 헤더 누락
     * - 403 Forbidden    — ADMIN 이외의 역할
     */
    @GetMapping("/api/v1/admin/users")
    public ResponseEntity<ApiResponse<Page<AdminUserResponse>>> getUsers(
        @ModelAttribute UserSearchRequest request, // 쿼리 파라미터를 record에 바인딩
        Pageable pageable                          // CustomPageableResolver가 size 제한 적용
    ) {
        // ADMIN 권한 체크
        AuthContext.requireRole(UserRole.ADMIN);

        // 검색 조건(Presentation DTO) → 도메인 스펙으로 변환 후 조회
        Page<UserSummaryData> page = userQueryService.getUsers(
            request.toSpec(), pageable
        );

        // Page<UserSummaryData> → Page<AdminUserResponse> 변환 (map: 각 요소 변환)
        return ApiResponse.success(
            UserSuccessCode.USER_LIST_FOUND,
            page.map(AdminUserResponse::from)
        );
    }

    /**
     * 사용자 단건 조회
     * GET /api/v1/admin/users/{userId}
     *
     * @param userId 조회할 사용자 UUID (Path Variable)
     * @return 200 OK + AdminUserResponse
     *
     * 오류 응답
     * - 401 Unauthorized — X-User-Role 헤더 누락
     * - 403 Forbidden    — ADMIN 이외의 역할
     * - 404 Not Found    — 사용자 없음 또는 Soft Deleted
     */
    @GetMapping("/api/v1/admin/users/{userId}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> getUserById(
        @PathVariable UUID userId
    ) {
        AuthContext.requireRole(UserRole.ADMIN);

        // Querydsl Projection으로 필요한 필드만 조회
        UserSummaryData data = userQueryService.getUserById(userId);

        return ApiResponse.success(
            UserSuccessCode.USER_FOUND,
            AdminUserResponse.from(data)
        );
    }

    /**
     * 사용자 Soft Delete (탈퇴 처리)
     * DELETE /api/v1/admin/users/{userId}
     *
     * @param userId 탈퇴 처리할 사용자 UUID (Path Variable)
     * @return 200 OK (탈퇴 완료 메시지)
     *
     * 오류 응답
     * - 401 Unauthorized — X-User-Role 헤더 누락
     * - 403 Forbidden    — ADMIN 이외의 역할
     * - 404 Not Found    — 사용자 없음
     * - 410 Gone         — 이미 탈퇴 처리된 사용자
     */
    @DeleteMapping("/api/v1/admin/users/{userId}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
        @PathVariable UUID userId
    ) {
        // getUserId()는 삭제를 수행한 관리자 UUID → deletedBy 기록에 사용
        AuthContext.requireRole(UserRole.ADMIN);
        UUID adminId = AuthContext.getUserId();

        userCommandService.deleteUser(adminId, userId);

        return ApiResponse.success(UserSuccessCode.USER_DELETED);
    }

    /**
     * 사용자 역할 변경
     * PATCH /api/v1/users/{userId}/role
     *
     * 설계 결정 사항
     * - 경로가 /admin/users 가 아닌 /users/{id}/role인 이유:
     *   RESTful 관점에서 역할은 사용자 리소스의 속성이므로 /users/{id}/role 경로가 적합합니다.
     *   단, 이 작업은 ADMIN 전용이므로 requireAdmin()으로 접근 제어합니다.
     *
     * @param userId  역할을 변경할 사용자 UUID (Path Variable)
     * @param request 변경할 역할 정보 (Request Body)
     * @return 200 OK + UserResponse (변경된 사용자 정보)
     *
     * 오류 응답
     * - 400 Bad Request  — role 필드 누락
     * - 401 Unauthorized — X-User-Role 헤더 누락
     * - 403 Forbidden    — ADMIN 이외의 역할
     * - 404 Not Found    — 사용자 없음 또는 탈퇴 상태
     */
    @PatchMapping("/api/v1/users/{userId}/role")
    public ResponseEntity<ApiResponse<UserResponse>> changeRole(
        @PathVariable UUID userId,
        @RequestBody @Valid ChangeRoleRequest request
    ) {
        AuthContext.requireRole(UserRole.ADMIN);

        // 역할 변경 후 UserResult 수신 → Presentation DTO로 변환
        UserResult result = userCommandService.changeRole(userId, request.role());

        return ApiResponse.success(
            UserSuccessCode.ROLE_CHANGED,
            UserResponse.from(result)
        );
    }
}
