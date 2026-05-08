package com.firstticket.userservice.infrastructure;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.firstticket.userservice.infrastructure.redis.AccessTokenBlacklistImpl;

@ExtendWith(MockitoExtension.class)
class AccessTokenBlacklistImplTest {

    @InjectMocks
    private AccessTokenBlacklistImpl blacklist;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private static final String JTI = "test-jti-uuid";

    @Nested
    @DisplayName("add()")
    class Add {

        @Test
        @DisplayName("유효한 TTL이면 Redis에 blacklist:{jti} 키로 저장한다")
        void 유효한_TTL_저장() {
            // given
            when(redisTemplate.opsForValue()).thenReturn(valueOps);

            // when
            blacklist.add(JTI, 900L); // 15분 잔여

            // then
            verify(valueOps).set(
                eq("blacklist:" + JTI), // key 형식 검증
                eq("1"),               // value는 존재 여부만 의미
                eq(900L),
                eq(TimeUnit.SECONDS)
            );
        }

        @Test
        @DisplayName("TTL이 0 이하이면 이미 만료된 토큰 — Redis 저장을 생략한다")
        void 만료된_토큰_저장_생략() {
            // given — TTL 음수 (이미 만료된 토큰)

            // when
            blacklist.add(JTI, 0L);
            blacklist.add(JTI, -1L);

            // then — Redis 호출 없어야 함
            verify(redisTemplate, never()).opsForValue();
        }
    }

    @Nested
    @DisplayName("isBlacklisted()")
    class IsBlacklisted {

        @Test
        @DisplayName("blacklist에 등록된 jti이면 true를 반환한다")
        void 등록된_jti_true() {
            // given
            when(redisTemplate.hasKey("blacklist:" + JTI)).thenReturn(true);

            // when & then
            assertThat(blacklist.isBlacklisted(JTI)).isTrue();
        }

        @Test
        @DisplayName("blacklist에 없는 jti이면 false를 반환한다")
        void 미등록_jti_false() {
            // given
            when(redisTemplate.hasKey("blacklist:" + JTI)).thenReturn(false);

            // when & then
            assertThat(blacklist.isBlacklisted(JTI)).isFalse();
        }

        @Test
        @DisplayName("Redis가 null을 반환하면 false로 처리한다")
        void Redis_null_반환_false() {
            // given — Redis hasKey()가 null 반환하는 엣지 케이스
            when(redisTemplate.hasKey(anyString())).thenReturn(null);

            // when & then
            assertThat(blacklist.isBlacklisted(JTI)).isFalse();
        }
    }
}
