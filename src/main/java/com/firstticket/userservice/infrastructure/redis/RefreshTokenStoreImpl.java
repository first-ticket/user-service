package com.firstticket.userservice.infrastructure.redis;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.firstticket.userservice.domain.service.RefreshTokenStore;

import lombok.RequiredArgsConstructor;

/**
 * domain/service/RefreshTokenStore 구현체
 *
 * 설계 결정 사항
 * - StringRedisTemplate 사용 - 값이 단순 String이므로 RedisTemplate에 비해 가볍고 직관적
 * - Redis key 패턴 : "refresh:{userId}" -> 사용자당 하나의 refresh Token만 유지 (단일 세션 정책)
 * - Refresh Token TTL 7일
 * - @Repository : infrastructure 계층의 데이터 접근 컴포넌트 명시
 */

@Repository
@RequiredArgsConstructor
public class RefreshTokenStoreImpl implements RefreshTokenStore {

    private final StringRedisTemplate redisTemplate;

    // Redis key prefix
    private static final String KEY_PREFIX = "refresh:";

    // Refresh Token TTL - Keycloak 설정과 동기화 (기본 7일)
    private static final long TTL_DAYS = 7L;

    /**
     * Refresh Token을 key로 Redis에 저장
     * 동일 userId에 이미 토큰이 있다면 덮어쓰기합니다. (TTL도 갱신)
     */
    @Override
    public void save(UUID userId, String refreshToken) {
        redisTemplate.opsForValue().set(
            KEY_PREFIX + userId,
            refreshToken,
            TTL_DAYS,
            TimeUnit.DAYS
        );
    }

    /**
     * 저장된 Refresh Token을 조회
     * 토큰이 없거나 TTL 만료인 경우 Optional.empty() 반환
     */
    @Override
    public Optional<String> find(UUID userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX + userId));
    }

    /**
     * Refresh Token 삭제
     * 로그아웃 또는 토큰 재발급시 기존 토큰 무효화
     */
    @Override
    public void delete(UUID userId) {
        redisTemplate.delete(KEY_PREFIX + userId);
    }

    /**
     * Token Rotation
     *
     * 설계 결정 사항
     * - 동일 키 ("refresh:{userId}")를 Redis SET 단일 명령으로 덮어씀
     */
    @Override
    public void rotate(UUID userId, String newRefreshToken) {
        save(userId, newRefreshToken);
    }
}
