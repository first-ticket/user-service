package com.firstticket.userservice.infrastructure.redis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.firstticket.userservice.domain.service.RefreshTokenStore;

import lombok.RequiredArgsConstructor;

/**
 * domain/service/RefreshTokenStore 구현체
 *
 * 설계 결정 사항
 * - StringRedisTemplate 사용 — 값이 단순 String이므로 RedisTemplate에 비해 가볍고 직관적
 * - Redis key 패턴: "refresh:{userId}" → 사용자당 하나의 Refresh Token만 유지 (단일 세션 정책)
 * - Refresh Token TTL 7일
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenStoreImpl implements RefreshTokenStore {

    private final StringRedisTemplate redisTemplate;

    private static final String ROTATE_SCRIPT =
        "local stored = redis.call('GET', KEYS[1]) " +
            "if stored == false then return -1 end " +
            "if stored ~= ARGV[1] then return 0 end " +
            "redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3]) " +
            "return 1";

    private static final long REFRESH_TOKEN_TTL_SECONDS = 7 * 24 * 60 * 60L;

    /**
     * Refresh Token을 Redis에 저장
     * 동일 userId에 이미 토큰이 있다면 덮어쓰기 (TTL도 갱신)
     */
    @Override
    public void save(UUID userId, String refreshToken) {
        redisTemplate.opsForValue().set(
            buildKey(userId),              // "refresh:{userId}"
            refreshToken,
            REFRESH_TOKEN_TTL_SECONDS,
            TimeUnit.SECONDS
        );
    }

    /**
     * 저장된 Refresh Token 조회
     * 토큰이 없거나 TTL 만료인 경우 Optional.empty() 반환
     */
    @Override
    public Optional<String> find(UUID userId) {
        return Optional.ofNullable(
            redisTemplate.opsForValue().get(buildKey(userId))
        );
    }

    /**
     * Refresh Token 삭제
     * 로그아웃 또는 세션 무효화 시 사용
     */
    @Override
    public void delete(UUID userId) {
        redisTemplate.delete(buildKey(userId)); // KEY_PREFIX 미정의 → buildKey()로 통일
    }

    /**
     * Token Rotation — Lua CAS (Compare-And-Swap)
     * oldToken 일치 확인 + newToken 저장을 Redis에서 원자적으로 처리
     * 동시 요청이 들어와도 하나만 성공하도록 보장
     */
    @Override
    public RotateResult rotate(UUID userId, String oldToken, String newToken) {
        RedisScript<Long> script = RedisScript.of(ROTATE_SCRIPT, Long.class);

        Long result = redisTemplate.execute(
            script,
            List.of(buildKey(userId)),
            oldToken,
            newToken,
            String.valueOf(REFRESH_TOKEN_TTL_SECONDS)
        );

        // Lua 반환값 → enum 매핑
        if (result == null || result == -1L) return RotateResult.NOT_FOUND;
        if (result == 0L)                    return RotateResult.TOKEN_MISMATCH;
        return RotateResult.SUCCESS;
    }

    // Redis key 생성
    private String buildKey(UUID userId) {
        return "refresh:" + userId;
    }
}
