package com.firstticket.userservice.infrastructure.persistence;

import com.firstticket.userservice.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * User 전용 Spring Data JPA Repository
 *
 * 역할
 * - JpaRepository로부터 기본 CRUD(save/findById/delete 등) 상속
 * - UserRepositoryImpl이 해당 인터페이스에 위임하여 UserRepository를 구현합니다.
 */
public interface UserJpaRepository extends JpaRepository<User, UUID> {

    /**
     * 이메일로 사용자 조회
     * Spring Data - "SELECT u FROM User u WHERE u.email = :email" 자동 생성
     */
    Optional<User> findByEmail(String email);

    /**
     * Keycloak sub(subject) 값으로 사용자 조회
     * JWT 토큰 검증 후 사용자 정보를 로드할 때 사용합니다.
     */
    Optional<User> findByKeycloakId(String keycloakId);

    /**
     * 이메일 존재 여부 확인 - COUNT 쿼리 대신 EXISTS 쿼리를 생성하여 성능 최적화
     * 회원가입 시 중복 이메일 체크에 사용합니다.
     */
    boolean existsByEmail(String email);
}
