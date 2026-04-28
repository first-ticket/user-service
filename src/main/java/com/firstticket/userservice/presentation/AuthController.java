package com.firstticket.userservice.presentation;

import com.firstticket.common.response.ApiResponse;
import com.firstticket.userservice.application.UserCommandService;
import com.firstticket.userservice.application.dto.result.TokenResult;
import com.firstticket.userservice.application.dto.result.UserResult;
import com.firstticket.userservice.presentation.dto.request.LoginRequest;
import com.firstticket.userservice.presentation.dto.request.SignupRequest;
import com.firstticket.userservice.presentation.dto.response.TokenResponse;
import com.firstticket.userservice.presentation.dto.response.UserResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth(인증) 관련 API 컨트롤러
 * Gateway SecurityConfig의 PUBLIC_PATHS에 등록된 경로 담당 (인증 불필요)
 *
 * 담당 엔드포인트:
 *   POST /api/v1/auth/signup - 회원가입
 *   POST /api/v1/auth/login  - 로그인
 */

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
     *   추후 docs로 교체 예정
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
}
