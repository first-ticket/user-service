package com.firstticket.userservice.domain.service;

import java.util.Optional;
import java.util.UUID;

/**
 * Refresh Token 저장소 Port
 *
 * 설계 결정 사항
 * - 구현체: infrastructure/redis/RefreshTokenStoreImpl (Redis 사용)
 * - key 패턴: "refresh:{userId}" / TTL: 7일 (구현체측에서 관리)
 * - 로그아웃·토큰 재발급 시 삭제(delete)도 본 인터페이스를 통해 처리합니다.
 */
public interface RefreshTokenStore {

    // Refresh Token Redis 키 저장 (신규 저장 또는 덮어쓰기)
    void save(UUID userId, String refreshToken);

    // Refresh Token 조회
    Optional<String> find(UUID userId);

    // Refresh Token 삭제 (로그아웃, 재발급 후 기존 토큰 무효화 로직)
    void delete(UUID userId);
}
