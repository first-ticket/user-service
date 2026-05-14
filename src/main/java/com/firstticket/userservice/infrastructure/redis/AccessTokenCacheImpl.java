package com.firstticket.userservice.infrastructure.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firstticket.userservice.domain.service.AccessTokenCache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


// domain/service/AccessTokenCache Port의 Redis 구현체

@Slf4j
@Component
@RequiredArgsConstructor
public class AccessTokenCacheImpl implements AccessTokenCache {

    private final StringRedisTemplate redisTemplate;
    // JWT payload 파싱에 사용
    private final ObjectMapper objectMapper;
    private static final String KEY_PREFIX = "at-cache:";
    private static final long DEFAULT_TTL_SECONDS = 1800L;

    @Override
    public void save(String email, String accessToken) {
        // JWT exp 기반으로 남은 유효 시간을 TTL로 계산
        long ttlSeconds = extractRemainingTtl(accessToken);

        // 이미 만료된 토큰을 캐시에 넣으면 즉시 redis에서 사라지거나 오류 발생 → 저장 자체를 생략
        if (ttlSeconds <= 0) {
            log.warn("[AT cache] 이미 만료된 Access Token - 캐시 저장 생략, email: {}", abbreviated(email));
            return;
        }

        // SET at-cache:{SHA-256(email)} "{accessToken}" EX {ttlSeconds}
        redisTemplate.opsForValue().set(
            buildKey(email),  // PII 보호를 위한 해시 키
            accessToken,      // AT 문자열 원문 (Base64 JWT)
            ttlSeconds,
            TimeUnit.SECONDS
        );

        log.debug("[AT cache] 캐시 저장 완료 - email: {}, ttl: {}s", abbreviated(email), ttlSeconds);
    }

    @Override
    public Optional<String> find(String email) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(buildKey(email)));
    }

    /**
     * 이메일에 해당하는 AT 캐시를 삭제
     * 로그아웃 / 비밀번호 변경 / 회원탈퇴 시 호출하여 stale 토큰 반환을 방지
     */
    @Override
    public void delete(String email) {
        // DEL at-cache:{SHA-256(email)}
        redisTemplate.delete(buildKey(email));
        log.debug("[AT cache] 캐시 삭제 완료 - email: {}", abbreviated(email));
    }

    /**
     * Redis 키 생성
     * email을 소문자로 정규화한 뒤 SHA-256 해시하여 PII 보호
     */
    private String buildKey(String email) {
        return KEY_PREFIX + sha256(email.toLowerCase(Locale.ROOT));
    }
        private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // 문자열을 UTF-8 바이트로 변환 후 해시
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));

            // byte 배열을 2자리 소문자 16진수 문자열로 변환 (총 64자)
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();

        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 Java SE 명세상 필수 지원 알고리즘이므로 이 분기는 실행되지 않음
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    /**
     * Access Token JWT payload에서 exp(만료 epoch초)를 읽어 현재 시각과의 차이를 반환
     * KeycloakAuthServiceImpl.extractSubject()와 동일한 Base64URL 디코딩 방식을 사용
     *
     * @return 남은 유효 시간(초). 파싱 실패 시 DEFAULT_TTL_SECONDS, 이미 만료 시 <=0
     */
    private long extractRemainingTtl(String accessToken) {
        try {
            // JWT 구조: {header}.{payload}.{signature} — 점(.)으로 3분할
            String[] parts = accessToken.split("\\.");
            if (parts.length != 3) {
                // 잘못된 JWT 형식 — 기본 TTL로 폴백
                log.warn("[AT cache] JWT 형식 오류 - 기본 TTL {}s 사용", DEFAULT_TTL_SECONDS);
                return DEFAULT_TTL_SECONDS;
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(addPadding(parts[1]));

            @SuppressWarnings("unchecked")
            Map<String, Object> claims = objectMapper.readValue(payloadBytes, Map.class);

            Object exp = claims.get("exp");
            if (exp == null) {
                log.warn("[AT cache] exp 클레임 없음 - 기본 TTL {}s 사용", DEFAULT_TTL_SECONDS);
                return DEFAULT_TTL_SECONDS;
            }

            long expEpoch = ((Number) exp).longValue();
            return expEpoch - Instant.now().getEpochSecond();

        } catch (Exception e) {
            // Base64 디코딩 실패, JSON 파싱 실패 등 → 기본 TTL로 fallback
            log.warn("[AT cache] exp 클레임 추출 실패, 기본 TTL {}s 사용: {}", DEFAULT_TTL_SECONDS, e.getMessage());
            return DEFAULT_TTL_SECONDS;
        }
    }

    /**
     * Base64URL 인코딩 문자열에 누락된 패딩(=)을 복원합니다.
     * JWT는 패딩 없이 인코딩하지만 Java의 Base64.getUrlDecoder()는 패딩을 요구
     */
    private String addPadding(String base64Url) {
        int remainder = base64Url.length() % 4;
        if (remainder == 2) return base64Url + "=="; // 2글자 부족
        if (remainder == 3) return base64Url + "=";  // 1글자 부족
        return base64Url;
    }

    /**
     * 로그에 이메일 원문이 노출되지 않도록 앞 3자리만 표시
     * ex) "user@example.com" → "use***"
     */
    private String abbreviated(String email) {
        if (email == null || email.length() <= 3) return "***";
        return email.substring(0, 3) + "***";
    }
}
