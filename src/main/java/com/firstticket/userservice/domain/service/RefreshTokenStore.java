package com.firstticket.userservice.domain.service;

import java.util.Optional;
import java.util.UUID;

/**
 * Refresh Token 저장소 Port
 *
 * 설계 결정 사항
 * - domain 계층에 인터페이스를 선언하여 infrastructure 직접 의존을 차단
 * - 사용자당 하나의 Refresh Token 만 유지 (단일 세션 정책)
 * - TTL 및 저장소 구현은 infrastructure 계층에서 결정
 * - 로그아웃·토큰 재발급 시 삭제(delete)도 같은 인터페이스를 통해 처리합니다.
 */
public interface RefreshTokenStore {

    // Refresh Token Redis 키 저장 (신규 저장 또는 덮어쓰기)
    void save(UUID userId, String refreshToken);

    // Refresh Token 조회
    Optional<String> find(UUID userId);

    // Refresh Token 삭제 (로그아웃, 재발급 후 기존 토큰 무효화 로직)
    void delete(UUID userId);

    // Token Rotation : 기존 Refresh Token을 신규 토큰으로 교체
    // 동일 키를 단일 SET 명령으로 덮어씀. (TTL도 갱신)
    void rotate(UUID userId, String newRefreshToken);
}
