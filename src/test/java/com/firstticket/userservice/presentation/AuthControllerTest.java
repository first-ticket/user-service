package com.firstticket.userservice.presentation;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.restdocs.headers.HeaderDocumentation.*;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.*;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.*;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.UUID;

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

import com.firstticket.common.exception.GlobalExceptionHandler;
import com.firstticket.userservice.application.UserCommandService;
import com.firstticket.userservice.application.dto.result.TokenResult;
import com.firstticket.userservice.application.dto.result.UserResult;
import com.firstticket.userservice.domain.UserRole;
import com.firstticket.userservice.domain.UserStatus;
import com.firstticket.userservice.domain.exception.UserErrorCode;
import com.firstticket.userservice.domain.exception.UserException;

import lombok.extern.slf4j.Slf4j;

/**
 * 설계 결정 사항
 * - AuthContext는 HttpServletRequest 헤더를 직접 읽으므로 MockedStatic 불필요
 *   → 헤더 포함 = 인증 성공, 헤더 생략 = BusinessException(UNAUTHORIZED) 발생
 * - AuthController.logout()은 @RequestHeader를 직접 사용하므로 헤더 기반 인증과 동일하게 처리
 * - signup / login / refreshToken 은 Gateway PUBLIC_PATHS → X-User-Id 헤더 불필요
 */

@Slf4j
@WebMvcTest(AuthController.class) // AuthController 슬라이스만 로드
@AutoConfigureRestDocs
@ActiveProfiles("test") // Config Server 비활성화 (application-test.yaml)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserCommandService userCommandService;

    @Nested
    @DisplayName("POST /api/v1/auth/signup - 회원가입")
    class Signup {

        @Test
        @DisplayName("유효한 입력으로 회원가입시 201 Created를 반환")
        void 회원가입_성공() throws Exception {
            // given — Application 서비스가 반환할 UserResult Mock
            UserResult result = new UserResult(
                UUID.randomUUID(), "test@example.com", "테스트유저",
                UserRole.CUSTOMER, UserStatus.ACTIVE
            );
            when(userCommandService.signup(any())).thenReturn(result);

            // when & then
            mockMvc.perform(post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "email":    "test@example.com",
                          "password": "password123",
                          "username": "테스트유저"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("USER_CREATED"))
                .andDo(document("auth-signup",                  // 스니펫 디렉토리명
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestFields(
                        // 요청 필드 문서화
                        fieldWithPath("email").description("이메일 주소 (최대 255자, 이메일 형식 필수)"),
                        fieldWithPath("password").description("비밀번호 (최소 8자 이상)"),
                        fieldWithPath("username").description("표시 이름")
                    ),
                    responseFields(
                        // 공통 응답 래퍼 필드
                        fieldWithPath("success").description("요청 성공 여부"),
                        fieldWithPath("code").description("응답 코드 (USER_CREATED)"),
                        fieldWithPath("message").description("응답 메시지"),
                        fieldWithPath("timestamp").description("응답 시각"),
                        // data 필드
                        fieldWithPath("data.id").description("생성된 사용자 UUID"),
                        fieldWithPath("data.email").description("이메일 주소"),
                        fieldWithPath("data.username").description("표시 이름"),
                        fieldWithPath("data.role").description("역할 (CUSTOMER / HOST / ADMIN)"),
                        fieldWithPath("data.status").description("계정 상태 (ACTIVE / LOCKED / DELETED)")
                    )
                ));
        }

        @Test
        @DisplayName("이미 사용 중인 이메일로 가입 시 409 Conflict를 반환한다")
        void 중복이메일_409() throws Exception {
            // given — DUPLICATE_EMAIL 예외 발생 시뮬레이션
            when(userCommandService.signup(any()))
                .thenThrow(new UserException(UserErrorCode.DUPLICATE_EMAIL));

            // when & then
            mockMvc.perform(post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "email":    "duplicate@example.com",
                          "password": "password123",
                          "username": "중복유저"
                        }
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"))
                .andDo(document("auth-signup-duplicate-email",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    responseFields(
                        // 에러 응답 공통 필드 (data 없음)
                        fieldWithPath("success").description("요청 성공 여부 (false)"),
                        fieldWithPath("code").description("에러 코드 (DUPLICATE_EMAIL)"),
                        fieldWithPath("message").description("에러 메시지"),
                        fieldWithPath("timestamp").description("응답 시각")
                    )
                ));
        }

        @Test
        @DisplayName("이메일 필드가 빈 값이면 400 Bad Request를 반환한다")
        void 입력오류_400() throws Exception {
            // given - @NotBlank 위반 (@Valid 검증 실패)
            // 서비스 호출 없이 Controller 바인딩 레벨에서 거부

            // when & then
            mockMvc.perform(post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "email":    "",
                          "password": "password123",
                          "username": "유저"
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andDo(document("auth-signup-invalid-input",
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
    @DisplayName("POST /api/v1/auth/login — 로그인")
    class Login {

        @Test
        @DisplayName("유효한 자격증명으로 로그인 시 200 OK + 토큰 반환")
        void 로그인_성공() throws Exception {
            // given — Keycloak ROPC + Redis 저장 완료 후 TokenResult 반환 시뮬레이션
            TokenResult result = new TokenResult(
                "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.access_token",
                "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.refresh_token"
            );
            when(userCommandService.login(any())).thenReturn(result);

            // when & then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "email":    "test@example.com",
                          "password": "password123"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("LOGIN_SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andDo(document("auth-login",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestFields(
                        fieldWithPath("email").description("이메일 주소"),
                        fieldWithPath("password").description("비밀번호")
                    ),
                    responseFields(
                        fieldWithPath("success").description("요청 성공 여부"),
                        fieldWithPath("code").description("응답 코드 (LOGIN_SUCCESS)"),
                        fieldWithPath("message").description("응답 메시지"),
                        fieldWithPath("timestamp").description("응답 시각"),
                        fieldWithPath("data.accessToken").description("JWT Access Token (Authorization: Bearer {token})"),
                        fieldWithPath("data.refreshToken").description("JWT Refresh Token (재발급 시 사용)")
                    )
                ));
        }

        @Test
        @DisplayName("잘못된 자격증명(이메일 없음·비밀번호 불일치·잠금계정)으로 로그인 시 401을 반환한다")
        void 자격증명_오류_401() throws Exception {
            // given — LOCKED / DELETED / 비밀번호 불일치 모두 동일 에러 코드 (Security by Obscurity)
            when(userCommandService.login(any()))
                .thenThrow(new UserException(UserErrorCode.INVALID_CREDENTIALS));

            // when & then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "email":    "notexist@example.com",
                          "password": "wrongpassword"
                        }
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andDo(document("auth-login-unauthorized",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    responseFields(
                        fieldWithPath("success").description("요청 성공 여부 (false)"),
                        fieldWithPath("code").description("에러 코드 (INVALID_CREDENTIALS)"),
                        fieldWithPath("message").description("에러 메시지"),
                        fieldWithPath("timestamp").description("응답 시각")
                    )
                ));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/logout — 로그아웃")
    class Logout {

        // Gateway AuthorizationHeaderFilter가 JWT에서 추출해 주입하는 헤더 테스트 픽스처
        private static final String TEST_JTI       = UUID.randomUUID().toString();
        private static final String TEST_TOKEN_EXP =
            String.valueOf(Instant.now().plusSeconds(900).getEpochSecond());

        @Test
        @DisplayName("필수 헤더가 모두 있으면 Refresh Token 삭제 + Access Token blacklist 등록 후 204를 반환한다")
        void 로그아웃_성공() throws Exception {
            // given — userCommandService.logout()은 void, 정상 처리 모킹
            doNothing().when(userCommandService).logout(any());
            String keycloakId = UUID.randomUUID().toString();

            // when & then
            mockMvc.perform(post("/api/v1/auth/logout")
                    // Gateway가 JWT 클레임에서 추출해 주입하는 3개 헤더
                    .header("X-User-Id",    keycloakId)    // sub: Keycloak 사용자 UUID
                    .header("X-Jti",        TEST_JTI)      // jti: Access Token blacklist key
                    .header("X-Token-Exp",  TEST_TOKEN_EXP)) // exp: Redis TTL 계산용 (epoch 초)
                .andExpect(status().isNoContent())          // 204 No Content
                .andDo(document("auth-logout",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestHeaders(
                        headerWithName("X-User-Id")
                            .description("Gateway가 JWT sub 클레임에서 추출하여 주입한 Keycloak 사용자 UUID"),
                        headerWithName("X-Jti")
                            .description("Gateway가 JWT jti 클레임에서 추출하여 주입한 JWT 고유 ID — Access Token blacklist 키"),
                        headerWithName("X-Token-Exp")
                            .description("Gateway가 JWT exp 클레임에서 추출하여 주입한 만료 시각 (epoch 초) — Redis TTL 계산용")
                    )
                    // 204 No Content — 응답 body 없음
                ));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/token/refresh — 토큰 재발급")
    class RefreshToken {

        @Test
        @DisplayName("유효한 Refresh Token으로 재발급 시 200 OK + 새 토큰 쌍을 반환한다")
        void 토큰_재발급_성공() throws Exception {
            // given - Token Rotation: 기존 RT 무효화 + 새 AT/RT 발급
            TokenResult result = new TokenResult(
                "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.new_access_token",
                "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.new_refresh_token"
            );
            when(userCommandService.refreshToken(any())).thenReturn(result);

            // when & then
            mockMvc.perform(post("/api/v1/auth/token/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "refreshToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.old_refresh_token"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TOKEN_REFRESHED"))
                .andDo(document("auth-token-refresh",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestFields(
                        fieldWithPath("refreshToken").description("현재 유효한 Refresh Token")
                    ),
                    responseFields(
                        fieldWithPath("success").description("요청 성공 여부"),
                        fieldWithPath("code").description("응답 코드 (TOKEN_REFRESHED)"),
                        fieldWithPath("message").description("응답 메시지"),
                        fieldWithPath("timestamp").description("응답 시각"),
                        fieldWithPath("data.accessToken").description("새로 발급된 Access Token"),
                        fieldWithPath("data.refreshToken").description("새로 발급된 Refresh Token (기존 토큰 무효화)")
                    )
                ));
        }

        @Test
        @DisplayName("만료·로그아웃·재사용 감지된 Refresh Token이면 401 Unauthorized를 반환한다")
        void 토큰_무효_401() throws Exception {
            // given — NOT_FOUND, TOKEN_MISMATCH 모두 동일한 INVALID_REFRESH_TOKEN 에러 반환
            when(userCommandService.refreshToken(any()))
                .thenThrow(new UserException(UserErrorCode.INVALID_REFRESH_TOKEN));

            // when & then
            mockMvc.perform(post("/api/v1/auth/token/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "refreshToken": "expired.or.invalid.token"
                        }
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andDo(document("auth-token-refresh-unauthorized",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    responseFields(
                        fieldWithPath("success").description("요청 성공 여부 (false)"),
                        fieldWithPath("code").description("에러 코드 (INVALID_REFRESH_TOKEN)"),
                        fieldWithPath("message").description("에러 메시지"),
                        fieldWithPath("timestamp").description("응답 시각")
                    )
                ));
        }
    }
}
