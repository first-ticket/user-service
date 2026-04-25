package com.firstticket.userservice.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * User Aggregate Command 전용 Repository 인터페이스
 *
 * 설계 결정 사항
 * - domain 계층에 repository 선언 → Spring Data 의존성 없이 순수 Java 인터페이스 구성
 * - 구현체는 infrastructure/persistence/UserRepositoryImpl
 * - Query 전용 메서드는 UserQueryRepository 로 분리 (CQRS)
 */
public interface UserRepository {

    // 저장 - 신규 생성 CREATE 및 업데이트 UPDATE 모두 처리
    User save(User user);

    /**
     * PK로 조회 (Soft Delete 처리된 사용자 포함)
     * 삭제 여부 확인은 호출자 책임 (현재 ADMIN 전용)
     */
    Optional<User> findById(UUID id);

    /**
     * 이메일로 조회 - 로그인, 중복 가입 확인 등에 사용
     * Soft Delete된 사용자도 반환할 수 있으므로 호출자가 status 확인 필요 (현재 ADMIN 전용)
     */
    Optional<User> findByEmail(String email);

    /**
     * Keycloak sub 값으로 조회 - JWT 토큰 검증 후 사용자 조회 시 사용
     */
    Optional<User> findByKeycloakId(String keycloakId);

    /**
     * 이메일 존재 여부 확인 - 회원가입 시 중복 체크에 사용
     * Soft Delete 처리된 사용자도 포함하여 이메일 재사용을 막습니다
     */
    boolean existsByEmail(String email);
}
