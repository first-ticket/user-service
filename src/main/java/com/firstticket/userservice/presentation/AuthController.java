package com.firstticket.userservice.presentation;

import com.firstticket.common.response.ApiResponse;
import com.firstticket.userservice.application.UserCommandService;
import com.firstticket.userservice.application.dto.result.TokenResult;
import com.firstticket.userservice.application.dto.result.UserResult;
import com.firstticket.userservice.presentation.dto.request.LoginRequest;
import com.firstticket.userservice.presentation.dto.request.RefreshTokenRequest;
import com.firstticket.userservice.presentation.dto.request.SignupRequest;
import com.firstticket.userservice.presentation.dto.response.TokenResponse;
import com.firstticket.userservice.presentation.dto.response.UserResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserCommandService userCommandService;

    /**
     * 회원가입 API
     *
     * @param request email, password, username (모두 필수)
     * @return 201 Created + 생성 사용자 정보 (id, email, username, role, status)
     *
     * 오류 응답:
     *   400 Bad Request — 입력값 형식 오류 (@NotBlank 위반, Email/Password VO 검증 실패)
     *   409 Conflict    — 이미 사용 중인 이메일
     */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserResponse>> signup(
        @Valid @RequestBody SignupRequest request) {

        UserResult result = userCommandService.signup(request.toCommand());

        return ApiResponse.success(
            UserSuccessCode.USER_CREATED,
            UserResponse.from(result)
        );
    }

    /**
     * 로그인 API
     * Keycloak Resource Owner Password Credentials (ROPC) 흐름으로 토큰을 발급합니다.
     *
     * @param request email, password (모두 필수)
     * @return 200 OK + { accessToken, refreshToken }
     *         accessToken: API 호출 시 Authorization: Bearer {token} 헤더에 포함
     *         refreshToken: 만료 시 POST /api/v1/auth/token/refresh 로 재발급
     *
     * 오류 응답:
     *   400 Bad Request  — email / password 빈 값 (@NotBlank 위반)
     *   401 Unauthorized — 잘못된 자격증명 (계정 없음, 비밀번호 불일치, LOCKED/DELETED 계정 포함)
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
        @Valid @RequestBody LoginRequest request) {

        // Application 계층 서비스에서 keycloak 인증 + Redis 저장까지 처리
        TokenResult result = userCommandService.login(request.toCommand());

        return ApiResponse.success(
            UserSuccessCode.LOGIN_SUCCESS,
            TokenResponse.from(result)
        );
    }

    /**
     * 로그아웃 API
     * Gateway에서 주입한 X-User-Id 헤더(keycloakId)를 사용하여 Redis의 Refresh Token을 삭제합니다.
     *
     * @param keycloakId Gateway AuthorizationHeaderFilter가 JWT sub 클레임에서 추출하여 주입
     * @return 204 No Content
     *
     * 오류 응답:
     *   404 Not Found — 존재하지 않는 사용자 (정상적인 요청에서는 발생하지 않음)
     *
     * 설계 결정 사항
     * - Access Token은 만료될 때까지 유효하지만 TTL이 짧으므로(통상 5~15분) 허용 범위로 간주
     * - Refresh Token만 삭제하여 재발급 경로를 차단하는 방식 채택
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        @RequestHeader("X-User-Id") String keycloakId) {

        userCommandService.logout(keycloakId);

        // 204 No Content — 응답 body 없음 (RESTful 관례: 삭제/무효화 성공 시 본문 생략)
        return ResponseEntity.noContent().build();
    }

    /**
     * 토큰 재발급 API (Token Rotation)
     * Refresh Token으로 새로운 Access Token + Refresh Token 쌍을 발급합니다.
     * 기존 Refresh Token은 즉시 무효화됩니다. (replay attack 방지)
     *
     * @param request refreshToken (필수)
     * @return 200 OK + { accessToken, refreshToken }
     *
     * 오류 응답:
     *   400 Bad Request  — refreshToken 빈 값 (@NotBlank 위반)
     *   401 Unauthorized — 유효하지 않은 Refresh Token (만료, 로그아웃, 재사용 공격 감지)
     *
     * 설계 결정 사항
     * - Gateway PUBLIC_PATHS 등록 경로 → X-User-Id 헤더 없음
     * - Refresh Token JWT payload에서 sub(keycloakId)를 추출하여 사용자 식별
     */
    @PostMapping("/token/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refreshToken(
        @Valid @RequestBody RefreshTokenRequest request) {

        TokenResult result = userCommandService.refreshToken(request.toCommand());

        return ApiResponse.success(
            UserSuccessCode.TOKEN_REFRESHED,
            TokenResponse.from(result)
        );
    }
}
