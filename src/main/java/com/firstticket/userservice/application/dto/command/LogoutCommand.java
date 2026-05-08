package com.firstticket.userservice.application.dto.command;

import java.util.Objects;

/**
 * 설계 결정 사항
 * - keycloakId: Gateway X-User-Id 헤더 -> Refresh Token 삭제에 사용
 * - jti: Gateway X-Jti 헤더 -> Access Token blacklist 등록 key
 * - tokenExpEpoch: Gateway X-Token-Exp 헤더 (epoch 초) -> Redis TTL 계산
 *   TTL = tokenExpEpoch - now (초 단위). 음수이면 이미 만료된 토큰
 */
public record LogoutCommand(
    String keycloakId,
    String jti,
    long tokenExpEpoch
) {
    // compact constructor - null 유입 방어
    public LogoutCommand {
        Objects.requireNonNull(keycloakId, "keycloakId는 null일 수 없습니다.");
        Objects.requireNonNull(jti, "jti는 null일 수 없습니다.");
    }
}
