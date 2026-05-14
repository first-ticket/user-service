package com.firstticket.userservice.domain.service;

import java.util.Optional;

/**
 * Access Token 캐시 저장소 Port
 *
 * 설계 결정 사항
 * - domain 계층에 인터페이스를 선언함으로 infrastructure(Redis) 직접 의존을 차단
 *   (RefreshTokenStore, AccessTokenBlacklist 패턴과 동일 원칙)
 * - 캐시 키 생성(SHA-256), TTL 계산(JWT exp 파싱)은 모두 구현체에서 담당
 * - 재로그인 시 Keycloak ROPC 호출(~135ms)을 생략하고 Redis 조회(~5ms)로 대체하는 성능 최적화가 목적
 */
public interface AccessTokenCache {

    /**
     * Access Token을 이메일 기반 캐시 키로 저장
     * TTL은 구현체가 JWT의 exp 클레임을 파싱하여 계산
     */
    void save(String email, String accessToken);

    /**
     * 이메일로 캐시된 Access Token을 조회
     * 캐시가 없거나 TTL이 만료된 경우 Optional.empty()를 반환
     */
    Optional<String> find(String email);

    /**
     * 이메일에 해당하는 Access Token 캐시를 삭제
     * 로그아웃 / 비밀번호 변경 / 회원탈퇴 시 stale 토큰 반환을 방지하기 위해 호출
     * @param email 사용자 이메일
     */
    void delete(String email);
}
