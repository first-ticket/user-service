package com.firstticket.userservice.presentation;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.restdocs.headers.HeaderDocumentation.*;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.*;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.*;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.firstticket.common.exception.GlobalExceptionHandler;
import com.firstticket.userservice.application.HostRequestCommandService;
import com.firstticket.userservice.application.HostRequestQueryService;
import com.firstticket.userservice.application.UserCommandService;
import com.firstticket.userservice.application.UserQueryService;
import com.firstticket.userservice.application.dto.result.HostRequestResult;
import com.firstticket.userservice.application.dto.result.UserResult;
import com.firstticket.userservice.domain.HostRequestStatus;
import com.firstticket.userservice.domain.UserRole;
import com.firstticket.userservice.domain.UserStatus;
import com.firstticket.userservice.domain.exception.UserErrorCode;
import com.firstticket.userservice.domain.exception.UserException;
import com.firstticket.userservice.domain.query.UserSummaryData;


/**
 * 설계 결정 사항
 * - AuthContext.requireRole(ADMIN) 검증을 헤더로 제어
 * - Page 응답 타입은 relaxedResponseFields() 를 사용합니다.
 *   이유: Page 직렬화 결과에 pageable, sort 등 Spring 내부 메타데이터 필드가 다수 포함되어
 *   responseFields()로 전수 문서화 시 관리 비용이 과도
 *   핵심 content 배열과 페이지 요약 필드만 문서화하고 나머지는 허용
 */
@WebMvcTest(AdminController.class)
@AutoConfigureRestDocs
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserQueryService userQueryService;

    @MockitoBean
    private UserCommandService userCommandService;

    @MockitoBean
    private HostRequestCommandService hostRequestCommandService;

    @MockitoBean
    private HostRequestQueryService hostRequestQueryService;

    // 공통 헤더 상수
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID TARGET_USER_ID = UUID.randomUUID();

    // ADMIN 검증용 헤더
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
    withAdminHeaders(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder) {
        return builder
            .header("X-User-Role", "ADMIN") // requireRole(ADMIN) 통과
            .header("X-User-Id", ADMIN_ID.toString()); // getUserId() 조회 가능
    }

    @Nested
    @DisplayName("GET /api/v1/admin/users - 사용자 목록 조회")
    class GetUsers {

        @Test
        @DisplayName("ADMIN이 조회 시 200 OK + 사용자 목록 페이지를 반환한다")
        void 목록_조회_성공() throws Exception {
            // given - Querydsl 동적 검색 결과 시뮬레이션
            UserSummaryData data = new UserSummaryData(
                TARGET_USER_ID, "user@example.com", "일반유저",
                UserRole.CUSTOMER, UserStatus.ACTIVE, LocalDateTime.now()
            );
            Page<UserSummaryData> page = new PageImpl<>(
                List.of(data), PageRequest.of(0, 10), 1
            );
            when(userQueryService.getUsers(any(), any())).thenReturn(page);

            // when & then
            mockMvc.perform(withAdminHeaders(get("/api/v1/admin/users"))
                    .param("page", "0")
                    .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("USER_LIST_FOUND"))
                .andExpect(jsonPath("$.data.content[0].email").value("user@example.com"))
                .andDo(document("admin-get-users",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestHeaders(
                        headerWithName("X-User-Role").description("역할 헤더 (ADMIN 필수)"),
                        headerWithName("X-User-Id").description("Gateway가 주입한 관리자 UUID")
                    ),
                    // Page 응답 전체 문서화는 relaxed 사용 (pageable, sort 등 내부 필드 허용)
                    relaxedResponseFields(
                        fieldWithPath("success").description("요청 성공 여부"),
                        fieldWithPath("code").description("응답 코드 (USER_LIST_FOUND)"),
                        fieldWithPath("message").description("응답 메시지"),
                        fieldWithPath("timestamp").description("응답 시각"),
                        fieldWithPath("data.content[].id").description("사용자 UUID"),
                        fieldWithPath("data.content[].email").description("이메일 주소"),
                        fieldWithPath("data.content[].username").description("표시 이름"),
                        fieldWithPath("data.content[].role").description("역할"),
                        fieldWithPath("data.content[].status").description("계정 상태"),
                        fieldWithPath("data.content[].createdAt").description("가입 일시"),
                        fieldWithPath("data.totalElements").description("전체 사용자 수"),
                        fieldWithPath("data.totalPages").description("전체 페이지 수"),
                        fieldWithPath("data.size").description("페이지 크기"),
                        fieldWithPath("data.number").description("현재 페이지 번호 (0-based)")
                    )
                ));
        }

        @Test
        @DisplayName("X-User-Role 헤더가 없으면 401 Unauthorized를 반환한다")
        void 인증_없음_401() throws Exception {
            // given — X-User-Role 헤더 없음 → AuthContext.getRole() → UNAUTHORIZED
            mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andDo(document("admin-unauthorized",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    responseFields(
                        fieldWithPath("success").description("요청 성공 여부 (false)"),
                        fieldWithPath("code").description("에러 코드 (UNAUTHORIZED)"),
                        fieldWithPath("message").description("에러 메시지"),
                        fieldWithPath("timestamp").description("응답 시각")
                    )
                ));
        }

        @Test
        @DisplayName("ADMIN이 아닌 역할로 접근 시 403 Forbidden을 반환한다")
        void 권한_없음_403() throws Exception {
            // given - X-User-Role: CUSTOMER → requireRole(ADMIN) → FORBIDDEN
            mockMvc.perform(get("/api/v1/admin/users")
                    .header("X-User-Role", "CUSTOMER")
                    .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andDo(document("admin-forbidden",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    responseFields(
                        fieldWithPath("success").description("요청 성공 여부 (false)"),
                        fieldWithPath("code").description("에러 코드 (FORBIDDEN)"),
                        fieldWithPath("message").description("에러 메시지"),
                        fieldWithPath("timestamp").description("응답 시각")
                    )
                ));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/users/{userId} - 사용자 단건 조회")
    class GetUserById {

        @Test
        @DisplayName("ADMIN이 존재하는 userId로 조회 시 200 OK + 사용자 정보를 반환한다")
        void 단건_조회_성공() throws Exception {
            // given
            UserSummaryData data = new UserSummaryData(
                TARGET_USER_ID, "user@example.com", "일반유저",
                UserRole.CUSTOMER, UserStatus.ACTIVE, LocalDateTime.now()
            );
            when(userQueryService.getUserById(any())).thenReturn(data);

            // when & then
            mockMvc.perform(withAdminHeaders(
                    get("/api/v1/admin/users/{userId}", TARGET_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("USER_FOUND"))
                .andExpect(jsonPath("$.data.id").value(TARGET_USER_ID.toString()))
                .andDo(document("admin-get-user-by-id",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestHeaders(
                        headerWithName("X-User-Role").description("역할 헤더 (ADMIN 필수)"),
                        headerWithName("X-User-Id").description("Gateway가 주입한 관리자 UUID")
                    ),
                    pathParameters(
                        parameterWithName("userId").description("조회할 사용자 UUID")
                    ),
                    responseFields(
                        fieldWithPath("success").description("요청 성공 여부"),
                        fieldWithPath("code").description("응답 코드 (USER_FOUND)"),
                        fieldWithPath("message").description("응답 메시지"),
                        fieldWithPath("timestamp").description("응답 시각"),
                        fieldWithPath("data.id").description("사용자 UUID"),
                        fieldWithPath("data.email").description("이메일 주소"),
                        fieldWithPath("data.username").description("표시 이름"),
                        fieldWithPath("data.role").description("역할"),
                        fieldWithPath("data.status").description("계정 상태"),
                        fieldWithPath("data.createdAt").description("가입 일시")
                    )
                ));
        }

        @Test
        @DisplayName("존재하지 않는 userId 조회 시 404 Not Found를 반환한다")
        void 사용자_없음_404() throws Exception {
            // given
            when(userQueryService.getUserById(any()))
                .thenThrow(new UserException(UserErrorCode.USER_NOT_FOUND));

            // when & then
            mockMvc.perform(withAdminHeaders(
                    get("/api/v1/admin/users/{userId}", UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andDo(document("admin-get-user-not-found",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    responseFields(
                        fieldWithPath("success").description("요청 성공 여부 (false)"),
                        fieldWithPath("code").description("에러 코드 (USER_NOT_FOUND)"),
                        fieldWithPath("message").description("에러 메시지"),
                        fieldWithPath("timestamp").description("응답 시각")
                    )
                ));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/admin/users/{userId} - 사용자 탈퇴 처리")
    class DeleteUser {

        @Test
        @DisplayName("ADMIN이 사용자 탈퇴 처리 시 200 OK를 반환한다")
        void 탈퇴처리_성공() throws Exception {
            // given — DB Soft Delete + Keycloak 비활성화
            doNothing().when(userCommandService).deleteUser(any(), any());

            // when & then
            mockMvc.perform(withAdminHeaders(
                    delete("/api/v1/admin/users/{userId}", TARGET_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("USER_DELETED"))
                .andDo(document("admin-delete-user",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestHeaders(
                        headerWithName("X-User-Role").description("역할 헤더 (ADMIN 필수)"),
                        headerWithName("X-User-Id").description("삭제를 수행한 관리자 UUID (deletedBy 기록에 사용)")
                    ),
                    pathParameters(
                        parameterWithName("userId").description("탈퇴 처리할 사용자 UUID")
                    ),
                    responseFields(
                        fieldWithPath("success").description("요청 성공 여부"),
                        fieldWithPath("code").description("응답 코드 (USER_DELETED)"),
                        fieldWithPath("message").description("응답 메시지"),
                        fieldWithPath("timestamp").description("응답 시각")
                    )
                ));
        }

        @Test
        @DisplayName("존재하지 않는 사용자 탈퇴 처리 시 404 Not Found를 반환한다")
        void 사용자_없음_404() throws Exception {
            // given
            doThrow(new UserException(UserErrorCode.USER_NOT_FOUND))
                .when(userCommandService).deleteUser(any(), any());

            // when & then
            mockMvc.perform(withAdminHeaders(
                    delete("/api/v1/admin/users/{userId}", UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }

        @Test
        @DisplayName("X-User-Role 헤더가 없으면 401 Unauthorized를 반환한다")
        void 인증_없음_401() throws Exception {
            mockMvc.perform(get("/api/v1/admin/users/{userId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("ADMIN이 아닌 역할로 접근 시 403 Forbidden을 반환한다")
        void 권한_없음_403() throws Exception {
            mockMvc.perform(get("/api/v1/admin/users/{userId}", UUID.randomUUID())
                    .header("X-User-Role", "CUSTOMER")
                    .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/users/{userId}/role - 사용자 역할 변경")
    class ChangeRole {

        @Test
        @DisplayName("ADMIN이 유효한 role로 변경 시 200 OK + 변경된 사용자 정보를 반환한다")
        void 역할_변경_성공() throws Exception {
            // given — CUSTOMER → HOST 역할 변경
            UserResult result = new UserResult(
                TARGET_USER_ID, "user@example.com", "일반유저",
                UserRole.HOST, UserStatus.ACTIVE
            );
            when(userCommandService.changeRole(any(), any())).thenReturn(result);

            // when & then
            mockMvc.perform(withAdminHeaders(
                    patch("/api/v1/users/{userId}/role", TARGET_USER_ID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "role": "HOST"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ROLE_CHANGED"))
                .andExpect(jsonPath("$.data.role").value("HOST"))
                .andDo(document("admin-change-role",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestHeaders(
                        headerWithName("X-User-Role").description("역할 헤더 (ADMIN 필수)"),
                        headerWithName("X-User-Id").description("Gateway가 주입한 관리자 UUID")
                    ),
                    pathParameters(
                        parameterWithName("userId").description("역할을 변경할 사용자 UUID")
                    ),
                    requestFields(
                        fieldWithPath("role").description("변경할 역할 (CUSTOMER / HOST / ADMIN)")
                    ),
                    responseFields(
                        fieldWithPath("success").description("요청 성공 여부"),
                        fieldWithPath("code").description("응답 코드 (ROLE_CHANGED)"),
                        fieldWithPath("message").description("응답 메시지"),
                        fieldWithPath("timestamp").description("응답 시각"),
                        fieldWithPath("data.id").description("사용자 UUID"),
                        fieldWithPath("data.email").description("이메일 주소"),
                        fieldWithPath("data.username").description("표시 이름"),
                        fieldWithPath("data.role").description("변경된 역할"),
                        fieldWithPath("data.status").description("계정 상태")
                    )
                ));
        }

        @Test
        @DisplayName("role 필드가 없으면 400 Bad Request를 반환한다")
        void 입력오류_400() throws Exception {
            // given — @NotNull 위반 → @Valid → MethodArgumentNotValidException → INVALID_INPUT

            // when & then
            mockMvc.perform(withAdminHeaders(
                    patch("/api/v1/users/{userId}/role", TARGET_USER_ID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andDo(document("admin-change-role-invalid",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    responseFields(
                        fieldWithPath("success").description("요청 성공 여부 (false)"),
                        fieldWithPath("code").description("에러 코드 (INVALID_INPUT)"),
                        fieldWithPath("message").description("에러 메시지"),
                        fieldWithPath("timestamp").description("응답 시각")
                    )
                ));
        }

        @Test
        @DisplayName("역할 헤더 없을 때 401 반환")
        void changeRole_401() throws Exception {
            mockMvc.perform(patch("/api/v1/users/{userId}/role", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"role\": \"HOST\"}"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ADMIN 아닌 역할은 403 반환")
        void changeRole_403() throws Exception {
            mockMvc.perform(patch("/api/v1/users/{userId}/role", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"role\": \"HOST\"}")
                    .header("X-User-Id", UUID.randomUUID().toString())
                    .header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/host-requests - HOST 신청 목록 조회")
    class GetHostRequests {

        @Test
        @DisplayName("ADMIN이 조회 시 200 OK + PENDING 상태의 신청 목록을 반환한다")
        void HOST_신청목록_조회_성공() throws Exception {
            // given
            HostRequestResult item = new HostRequestResult(
                UUID.randomUUID(), TARGET_USER_ID,
                HostRequestStatus.PENDING, LocalDateTime.now()
            );
            Page<HostRequestResult> page = new PageImpl<>(
                List.of(item), PageRequest.of(0, 10), 1
            );
            when(hostRequestQueryService.getHostRequests(any())).thenReturn(page);

            // when & then
            mockMvc.perform(withAdminHeaders(get("/api/v1/admin/host-requests"))
                    .param("page", "0")
                    .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("HOST_REQUEST_LIST_FOUND"))
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING"))
                .andDo(document("admin-get-host-requests",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestHeaders(
                        headerWithName("X-User-Role").description("역할 헤더 (ADMIN 필수)"),
                        headerWithName("X-User-Id").description("Gateway가 주입한 관리자 UUID")
                    ),
                    relaxedResponseFields(
                        fieldWithPath("success").description("요청 성공 여부"),
                        fieldWithPath("code").description("응답 코드 (HOST_REQUEST_LIST_FOUND)"),
                        fieldWithPath("message").description("응답 메시지"),
                        fieldWithPath("timestamp").description("응답 시각"),
                        fieldWithPath("data.content[].id").description("HOST 신청 UUID"),
                        fieldWithPath("data.content[].userId").description("신청자 UUID"),
                        fieldWithPath("data.content[].status").description("신청 상태 (PENDING)"),
                        fieldWithPath("data.content[].createdAt").description("신청 일시"),
                        fieldWithPath("data.totalElements").description("전체 신청 건수"),
                        fieldWithPath("data.totalPages").description("전체 페이지 수")
                    )
                ));
        }

        @Test
        @DisplayName("X-User-Role 헤더가 없으면 401 Unauthorized를 반환한다")
        void 인증_없음_401() throws Exception {
            mockMvc.perform(get("/api/v1/admin/host-requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("ADMIN이 아닌 역할로 접근 시 403 Forbidden을 반환한다")
        void 권한_없음_403() throws Exception {
            mockMvc.perform(get("/api/v1/admin/host-requests")
                    .header("X-User-Role", "HOST")
                    .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/admin/host-requests/{requestId} - HOST 신청 처리")
    class ApproveOrReject {

        @Test
        @DisplayName("PENDING 신청을 APPROVE 하면 200 OK + APPROVED 상태의 결과를 반환한다")
        void HOST_신청_승인_성공() throws Exception {
            // given
            UUID requestId = UUID.randomUUID();
            HostRequestResult result = new HostRequestResult(
                requestId, TARGET_USER_ID,
                HostRequestStatus.APPROVED, LocalDateTime.now()
            );
            when(hostRequestCommandService.approveOrReject(any(), any(), any()))
                .thenReturn(result);

            // when & then
            mockMvc.perform(withAdminHeaders(
                    patch("/api/v1/admin/host-requests/{requestId}", requestId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "action": "APPROVE"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("HOST_REQUEST_PROCESSED"))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andDo(document("admin-approve-host-request",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestHeaders(
                        headerWithName("X-User-Role").description("역할 헤더 (ADMIN 필수)"),
                        headerWithName("X-User-Id").description("승인을 수행한 관리자 UUID")
                    ),
                    pathParameters(
                        parameterWithName("requestId").description("처리할 HOST 신청 UUID")
                    ),
                    requestFields(
                        fieldWithPath("action").description("처리 액션 (APPROVE 또는 REJECT)")
                    ),
                    responseFields(
                        fieldWithPath("success").description("요청 성공 여부"),
                        fieldWithPath("code").description("응답 코드 (HOST_REQUEST_PROCESSED)"),
                        fieldWithPath("message").description("응답 메시지"),
                        fieldWithPath("timestamp").description("응답 시각"),
                        fieldWithPath("data.id").description("HOST 신청 UUID"),
                        fieldWithPath("data.userId").description("신청자 UUID"),
                        fieldWithPath("data.status").description("처리 후 상태 (APPROVED / REJECTED)"),
                        fieldWithPath("data.createdAt").description("신청 일시")
                    )
                ));
        }

        @Test
        @DisplayName("존재하지 않는 신청 ID 처리 시 404 Not Found를 반환한다")
        void 신청_없음_404() throws Exception {
            // given
            when(hostRequestCommandService.approveOrReject(any(), any(), any()))
                .thenThrow(new UserException(UserErrorCode.HOST_REQUEST_NOT_FOUND));

            // when & then
            mockMvc.perform(withAdminHeaders(
                    patch("/api/v1/admin/host-requests/{requestId}", UUID.randomUUID()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "action": "APPROVE"
                        }
                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOST_REQUEST_NOT_FOUND"))
                .andDo(document("admin-host-request-not-found",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    responseFields(
                        fieldWithPath("success").description("요청 성공 여부 (false)"),
                        fieldWithPath("code").description("에러 코드 (HOST_REQUEST_NOT_FOUND)"),
                        fieldWithPath("message").description("에러 메시지"),
                        fieldWithPath("timestamp").description("응답 시각")
                    )
                ));
        }

        @Test
        @DisplayName("이미 처리된 신청을 재처리 시 400 Bad Request를 반환한다")
        void 이미_처리된_신청_400() throws Exception {
            // given — APPROVED → APPROVE 재시도 → INVALID_HOST_REQUEST_STATUS (400)
            when(hostRequestCommandService.approveOrReject(any(), any(), any()))
                .thenThrow(new UserException(UserErrorCode.INVALID_HOST_REQUEST_STATUS));

            // when & then
            mockMvc.perform(withAdminHeaders(
                    patch("/api/v1/admin/host-requests/{requestId}", UUID.randomUUID()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "action": "APPROVE"
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_HOST_REQUEST_STATUS"))
                .andDo(document("admin-host-request-invalid-status",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    responseFields(
                        fieldWithPath("success").description("요청 성공 여부 (false)"),
                        fieldWithPath("code").description("에러 코드 (INVALID_HOST_REQUEST_STATUS)"),
                        fieldWithPath("message").description("에러 메시지"),
                        fieldWithPath("timestamp").description("응답 시각")
                    )
                ));
        }

        @Test
        @DisplayName("action 필드가 없으면 400 Bad Request를 반환한다")
        void 입력오류_400() throws Exception {
            // given — @NotNull 위반 → HttpMessageNotReadableException (enum 역직렬화 실패 포함)

            // when & then
            mockMvc.perform(withAdminHeaders(
                    patch("/api/v1/admin/host-requests/{requestId}", UUID.randomUUID()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("역할 헤더 없을 때 401 반환")
        void changeRole_401() throws Exception {
            mockMvc.perform(patch("/api/v1/users/{userId}/role", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"role\": \"HOST\"}"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ADMIN 아닌 역할은 403 반환")
        void changeRole_403() throws Exception {
            mockMvc.perform(patch("/api/v1/users/{userId}/role", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"role\": \"HOST\"}")
                    .header("X-User-Id", UUID.randomUUID().toString())
                    .header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isForbidden());
        }
    }
}
