package com.firstticket.userservice.domain.service;

/**
 * Access Token Blacklist 저장소 Port
 *
 * 설계 결정 사항
 * - domain 계층 인터페이스로 선언하여 infrastructure(Redis) 직접 의존 차단
 * - 키: "blacklist:{jti}" - Access Token의 JWT ID 클레임 기반
 * - TTL: 로그아웃 시점의 토큰 잔여 만료 시간 → TTL 경과 후 Redis에서 자동 삭제
 */
public interface AccessTokenBlacklist {

    // Access Token을 blacklist에 등록
    void add(String jti, long ttlSeconds);

    // Access Token이 blacklist에 등록되어 있는지 확인
    boolean isBlacklisted(String jti);
}
