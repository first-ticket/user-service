package com.firstticket.userservice.infrastructure.redis;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.firstticket.userservice.domain.service.AccessTokenBlacklist;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AccessTokenBlacklist Redis 구현체
 *
 * 설계 결정 사항
 * - key 패턴: "blacklist:{jti}"
 * - value: "1" (존재 여부만 체크)
 * - TTL은 호출자(UserCommandService)가 계산해서 전달 - 구현체는 저장만 담당
 * - Gateway가 같은 Redis 인스턴스를 바라보므로 prefix 충돌에 유의
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessTokenBlacklistImpl implements AccessTokenBlacklist {

    // StringRedisTemplate - RefreshTokenStoreImpl과 동일한 빈 재사용
    private final StringRedisTemplate redisTemplate;

    // Redis key 네임스페이스 - "refresh:" 와 구분
    private static final String KEY_PREFIX = "blacklist:";

    @Override
    public void add(String jti, long ttlSeconds) {
        // SET blacklist:{jti} "1" EX {ttlSeconds}
        // ttlSeconds <= 0이면 이미 만료된 토큰이므로 저장 생략
        if (ttlSeconds <= 0) {
            log.debug("[blacklist] 이미 만료된 토큰 - blacklist 등록 생략, jti prefix: {}", abbreviated(jti));
            return;
        }
        redisTemplate.opsForValue().set(
            buildKey(jti),   // "blacklist:{jti}"
            "1",             // 값은 존재 여부만 필요 - "1" 고정
            ttlSeconds,
            TimeUnit.SECONDS
        );
    }

    @Override
    public boolean isBlacklisted(String jti) {
        // Boolean.TRUE.equals(null) == false — null 반환 시 NPE 없이 false 처리
        // StringRedisTemplate.hasKey()는 연결 이상 등 일부 케이스에서 null을 반환할 수 있음
        return Boolean.TRUE.equals(redisTemplate.hasKey(buildKey(jti)));
    }

    // Redis key 생성: "blacklist:{jti}"
    private String buildKey(String jti) {
        return KEY_PREFIX + jti;
    }

    //  원문 노출 방지
    private String abbreviated(String jti) {
        if (jti == null || jti.length() <= 8) return "****";
        return jti.substring(0, 8) + "...";
    }
}
