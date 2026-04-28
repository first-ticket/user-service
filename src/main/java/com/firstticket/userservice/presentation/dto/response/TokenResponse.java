package com.firstticket.userservice.presentation.dto.response;

import com.firstticket.userservice.application.dto.result.TokenResult;

/**
 * 로그인 성공 응답 presentation 계층 DTO
 *
 * 설계 결정 사항
 * - Application 결과(TokenResult) -> Presentation 응답(TokenResponse) 2단 변환
 *   TokenResult 를 그대로 반환하면 Application 계층이 Presentation에 노출되는 결합 발생
 * - 향후 expiresIn, tokenType등 필드를 추가할 경우 TokenResult / TokenResponse를 독립적으로 확장 가능
 */
public record TokenResponse(
    String accessToken,
    String refreshToken
) {

    /**
     * TokenResult(Application) -> TokenResponse(Presentation) 변환 정적 팩토리 메서드
     */
    public static TokenResponse from(TokenResult result) {
        return new TokenResponse(result.accessToken(), result.refreshToken());
    }
}
