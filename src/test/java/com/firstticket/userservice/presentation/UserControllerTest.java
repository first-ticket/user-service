package com.firstticket.userservice.presentation;

import com.firstticket.common.exception.GlobalExceptionHandler;
import com.firstticket.userservice.application.HostRequestCommandService;
import com.firstticket.userservice.application.UserCommandService;
import com.firstticket.userservice.application.UserQueryService;
import com.firstticket.userservice.application.dto.result.HostRequestResult;
import com.firstticket.userservice.application.dto.result.UserResult;
import com.firstticket.userservice.domain.HostRequestStatus;
import com.firstticket.userservice.domain.UserRole;
import com.firstticket.userservice.domain.UserStatus;
import com.firstticket.userservice.domain.exception.UserErrorCode;
import com.firstticket.userservice.domain.exception.UserException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 설계 결정 사항
 * - AuthContext.getUserId()는 HttpServletRequest에서 X-User-Id 헤더를 직접 읽습니다.
 *   @WebMvcTest 환경에서 MockMvc가 실제 HttpServletRequest를 생성하므로
 *   MockedStatic 없이 헤더 포함/제외만으로 인증 성공/실패를 제어할 수 있습니다.
 *     · 헤더 포함  → AuthContext.getUserId() 정상 반환 → 200/201
 *     · 헤더 생략  → BusinessException(UNAUTHORIZED) 발생 → 401
 */
@WebMvcTest(UserController.class)
@AutoConfigureRestDocs
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserQueryService userQueryService;

    @MockitoBean
    private UserCommandService userCommandService;

    @MockitoBean
    private HostRequestCommandService hostRequestCommandService;

    // 공통 fixture - 테스트마다 재사용되는 사용자 Mock 데이터
    private static final UUID USER_ID = UUID.randomUUID();

    private UserResult userResultFixture() {
        return new UserResult(
            USER_ID, "test@example.com", "테스트유저",
            UserRole.CUSTOMER, UserStatus.ACTIVE
        );
    }

    @Nested
    @DisplayName("GET /api/v1/users/me - 내 정보 조회")
    class GetMyInfo {

        @Test
        @DisplayName("X-User-Id 헤더가 있으면 200 OK + 사용자 정보를 반환한다")
        void 내정보_조회_성공() throws Exception {
            // given
            when(userQueryService.getMyInfo(any())).thenReturn(userResultFixture());

            // when & then
            mockMvc.perform(get("/api/v1/users/me")
                    .header("X-User-Id", USER_ID.toString()))  // Gateway 주입 헤더
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("USER_FOUND"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andDo(document("user-get-my-info",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestHeaders(
                        headerWithName("X-User-Id").description("Gateway가 JWT sub 클레임에서 추출하여 주입한 사용자 UUID")
                    ),
                    responseFields(
                        fieldWithPath("success").description("요청 성공 여부"),
                        fieldWithPath("code").description("응답 코드 (USER_FOUND)"),
                        fieldWithPath("message").description("응답 메시지"),
                        fieldWithPath("timestamp").description("응답 시각"),
                        fieldWithPath("data.id").description("사용자 UUID"),
                        fieldWithPath("data.email").description("이메일 주소"),
                        fieldWithPath("data.username").description("표시 이름"),
                        fieldWithPath("data.role").description("역할 (CUSTOMER / HOST / ADMIN)"),
                        fieldWithPath("data.status").description("계정 상태 (ACTIVE / LOCKED / DELETED)")
                    )
                ));
        }

        @Test
        @DisplayName("X-User-Id 헤더가 없으면 401 Unauthorized를 반환한다")
        void 인증_헤더_없음_401() throws Exception {
            // given - X-User-Id 헤더 없이 요청

            // when & then
            mockMvc.perform(get("/api/v1/users/me"))   // 헤더 미포함
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andDo(document("user-unauthorized",
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
        @DisplayName("존재하지 않거나 탈퇴된 사용자 조회 시 404 Not Found를 반환한다")
        void 사용자_없음_404() throws Exception {
            // given - keycloakId로 조회했으나 DB에 없거나 DELETED 상태
            when(userQueryService.getMyInfo(any()))
                .thenThrow(new UserException(UserErrorCode.USER_NOT_FOUND));

            // when & then
            mockMvc.perform(get("/api/v1/users/me")
                    .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andDo(document("user-get-my-info-not-found",
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
    @DisplayName("PATCH /api/v1/users/me — 내 정보 수정")
    class UpdateProfile {

        @Test
        @DisplayName("유효한 username으로 수정 시 200 OK + 수정된 사용자 정보를 반환한다")
        void 내정보_수정_성공() throws Exception {
            // given
            UserResult updated = new UserResult(
                USER_ID, "test@example.com", "변경된이름",
                UserRole.CUSTOMER, UserStatus.ACTIVE
            );
            when(userCommandService.updateProfile(any(), any())).thenReturn(updated);

            // when & then
            mockMvc.perform(patch("/api/v1/users/me")
                    .header("X-User-Id", USER_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "username": "변경된이름"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PROFILE_UPDATED"))
                .andExpect(jsonPath("$.data.username").value("변경된이름"))
                .andDo(document("user-update-profile",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestHeaders(
                        headerWithName("X-User-Id").description("Gateway가 주입한 사용자 UUID")
                    ),
                    requestFields(
                        fieldWithPath("username").description("변경할 표시 이름 (최대 50자)")
                    ),
                    responseFields(
                        fieldWithPath("success").description("요청 성공 여부"),
                        fieldWithPath("code").description("응답 코드 (PROFILE_UPDATED)"),
                        fieldWithPath("message").description("응답 메시지"),
                        fieldWithPath("timestamp").description("응답 시각"),
                        fieldWithPath("data.id").description("사용자 UUID"),
                        fieldWithPath("data.email").description("이메일 주소"),
                        fieldWithPath("data.username").description("변경된 표시 이름"),
                        fieldWithPath("data.role").description("역할"),
                        fieldWithPath("data.status").description("계정 상태")
                    )
                ));
        }

        @Test
        @DisplayName("username이 빈 값이면 400 Bad Request를 반환한다")
        void 입력오류_400() throws Exception {
            // given - @NotBlank 위반 → GlobalExceptionHandler.handleValidException → INVALID_INPUT

            // when & then
            mockMvc.perform(patch("/api/v1/users/me")
                    .header("X-User-Id", USER_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "username": ""
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andDo(document("user-update-profile-invalid",
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
    }

    @Nested
    @DisplayName("DELETE /api/v1/users/me - 회원 탈퇴")
    class Withdraw {

        @Test
        @DisplayName("X-User-Id 헤더가 있으면 DB Soft Delete + Keycloak 비활성화 후 200 OK를 반환한다")
        void 회원탈퇴_성공() throws Exception {
            // given — DB softDelete + Keycloak disableUser + Redis RT 삭제
            doNothing().when(userCommandService).withdraw(any());

            // when & then
            mockMvc.perform(delete("/api/v1/users/me")
                    .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("WITHDRAW_SUCCESS"))
                .andDo(document("user-withdraw",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestHeaders(
                        headerWithName("X-User-Id").description("Gateway가 주입한 사용자 UUID")
                    ),
                    responseFields(
                        fieldWithPath("success").description("요청 성공 여부"),
                        fieldWithPath("code").description("응답 코드 (WITHDRAW_SUCCESS)"),
                        fieldWithPath("message").description("응답 메시지"),
                        fieldWithPath("timestamp").description("응답 시각")
                    )
                ));
        }

        @Test
        @DisplayName("X-User-Id 헤더가 없으면 401 Unauthorized를 반환한다")
        void 인증_없음_401() throws Exception {
            // when & then - 헤더 미포함 → AuthContext.getUserId() → UNAUTHORIZED
            mockMvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/users/me/host-requests - HOST 신청")
    class RequestHost {

        @Test
        @DisplayName("CUSTOMER 사용자가 최초 신청 시 201 Created + PENDING 상태의 신청 정보를 반환한다")
        void HOST_신청_성공() throws Exception {
            // given
            HostRequestResult result = new HostRequestResult(
                UUID.randomUUID(),               // 신청 ID
                USER_ID,                         // 신청자 DB PK
                HostRequestStatus.PENDING,
                LocalDateTime.now()
            );
            when(hostRequestCommandService.request(any())).thenReturn(result);

            // when & then
            mockMvc.perform(post("/api/v1/users/me/host-requests")
                    .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("HOST_REQUEST_CREATED"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andDo(document("user-host-request",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestHeaders(
                        headerWithName("X-User-Id").description("Gateway가 주입한 사용자 UUID")
                    ),
                    responseFields(
                        fieldWithPath("success").description("요청 성공 여부"),
                        fieldWithPath("code").description("응답 코드 (HOST_REQUEST_CREATED)"),
                        fieldWithPath("message").description("응답 메시지"),
                        fieldWithPath("timestamp").description("응답 시각"),
                        fieldWithPath("data.id").description("HOST 신청 UUID"),
                        fieldWithPath("data.userId").description("신청자 UUID (DB PK)"),
                        fieldWithPath("data.status").description("신청 상태 (PENDING)"),
                        fieldWithPath("data.createdAt").description("신청 일시")
                    )
                ));
        }

        @Test
        @DisplayName("이미 PENDING 신청이 있으면 409 Conflict를 반환한다")
        void 중복신청_409() throws Exception {
            // given — Application 레벨 또는 DB Partial Unique Index 충돌
            when(hostRequestCommandService.request(any()))
                .thenThrow(new UserException(UserErrorCode.HOST_REQUEST_ALREADY_PENDING));

            // when & then
            mockMvc.perform(post("/api/v1/users/me/host-requests")
                    .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOST_REQUEST_ALREADY_PENDING"))
                .andDo(document("user-host-request-conflict",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    responseFields(
                        fieldWithPath("success").description("요청 성공 여부 (false)"),
                        fieldWithPath("code").description("에러 코드 (HOST_REQUEST_ALREADY_PENDING)"),
                        fieldWithPath("message").description("에러 메시지"),
                        fieldWithPath("timestamp").description("응답 시각")
                    )
                ));
        }

        @Test
        @DisplayName("X-User-Id 헤더가 없으면 401 Unauthorized를 반환한다")
        void 인증_없음_401() throws Exception {
            // when & then
            mockMvc.perform(post("/api/v1/users/me/host-requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/users/me/password - 비밀번호 변경")
    class ChangePassword {

        @Test
        @DisplayName("유효한 현재/새 비밀번호로 요청 시 200 OK + PASSWORD_CHANGED를 반환한다")
        void 비밀번호_변경_성공() throws Exception {
            // given — void 반환 메서드이므로 doNothing 사용
            doNothing().when(userCommandService).changePassword(any(), any());

            // when & then
            mockMvc.perform(post("/api/v1/users/me/password")
                    .header("X-User-Id", USER_ID.toString())   // Gateway가 JWT sub에서 주입한 헤더
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                      {
                        "currentPassword": "CurrentPass1!",
                        "newPassword": "NewPassword1!"
                      }
                      """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGED"))
                .andDo(document("user-change-password",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestHeaders(
                        headerWithName("X-User-Id").description("Gateway가 JWT sub 클레임에서 추출하여 주입한 사용자 UUID")
                    ),
                    requestFields(
                        fieldWithPath("currentPassword").description("현재 사용 중인 비밀번호"),
                        fieldWithPath("newPassword").description("변경할 새 비밀번호 (8자 이상)")
                    ),
                    responseFields(
                        fieldWithPath("success").description("요청 성공 여부"),
                        fieldWithPath("code").description("응답 코드 (PASSWORD_CHANGED)"),
                        fieldWithPath("message").description("응답 메시지"),
                        fieldWithPath("timestamp").description("응답 시각")
                    )
                ));
        }

        @Test
        @DisplayName("currentPassword가 빈 값이면 @NotBlank 위반으로 400 Bad Request를 반환한다")
        void currentPassword_빈값_400() throws Exception {
            // given — @NotBlank 검증 실패 → GlobalExceptionHandler → INVALID_INPUT (400)

            mockMvc.perform(post("/api/v1/users/me/password")
                    .header("X-User-Id", USER_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                      {
                        "currentPassword": "",
                        "newPassword": "NewPassword1!"
                      }
                      """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andDo(document("user-change-password-invalid-current",
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
        @DisplayName("newPassword가 빈 값이면 @NotBlank 위반으로 400 Bad Request를 반환한다")
        void newPassword_빈값_400() throws Exception {
            // given — @NotBlank 검증 실패 → GlobalExceptionHandler → INVALID_INPUT (400)

            mockMvc.perform(post("/api/v1/users/me/password")
                    .header("X-User-Id", USER_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                      {
                        "currentPassword": "CurrentPass1!",
                        "newPassword": ""
                      }
                      """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andDo(document("user-change-password-invalid-new",
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
        @DisplayName("현재 비밀번호가 틀리면 422 Unprocessable Entity + WRONG_CURRENT_PASSWORD를 반환한다")
        void 현재_비밀번호_불일치_422() throws Exception {   // 메서드명도 422로 변경
            doThrow(new UserException(UserErrorCode.WRONG_CURRENT_PASSWORD))
                .when(userCommandService).changePassword(any(), any());

            mockMvc.perform(post("/api/v1/users/me/password")
                    .header("X-User-Id", USER_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                  {
                    "currentPassword": "WrongPass1!",
                    "newPassword": "NewPassword1!"
                  }
                  """))
                .andExpect(status().isUnprocessableEntity())    // 422
                .andExpect(jsonPath("$.code").value("WRONG_CURRENT_PASSWORD"))
                .andDo(document("user-change-password-wrong-current",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    responseFields(
                        fieldWithPath("success").description("요청 성공 여부 (false)"),
                        fieldWithPath("code").description("에러 코드 (WRONG_CURRENT_PASSWORD)"),
                        fieldWithPath("message").description("에러 메시지"),
                        fieldWithPath("timestamp").description("응답 시각")
                    )
                ));
        }

        @Test
        @DisplayName("X-User-Id 헤더가 없으면 401 Unauthorized + UNAUTHORIZED를 반환한다")
        void 인증_헤더_없음_401() throws Exception {
            // given — Gateway가 X-User-Id를 주입하지 않은 경우 (비인증 요청)
            // AuthContext.getUserId() → BusinessException(UNAUTHORIZED)

            mockMvc.perform(post("/api/v1/users/me/password")
                    .contentType(MediaType.APPLICATION_JSON)   // X-User-Id 헤더 생략
                    .content("""
                      {
                        "currentPassword": "CurrentPass1!",
                        "newPassword": "NewPassword1!"
                      }
                      """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }
}
