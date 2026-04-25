package com.firstticket.userservice.infrastructure.persistence;

import com.firstticket.userservice.domain.User;
import com.firstticket.userservice.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * UserRepository domain 계층 인터페이스의 JPA 구현체
 *
 * 설계 결정 사항
 * - Adapter 패턴: domain.UserRepository(port) ← infrastructure.UserRepositoryImpl(adapter)
 * - UserJpaRepository에 실제 DB 작업을 위임
 * - domain 계층이 Spring Data에 직접 의존하지 않도록 격리하는 역할 수행
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    // Spring Data JPA Repository - 실제 DB 작업 수행
    private final UserJpaRepository userJpaRepository;

    @Override
    public User save(User user) {
        // JpaRepository.save()
        // 신규 → INSERT, 기존 → UPDATE
        return userJpaRepository.save(user);
    }

    @Override
    public Optional<User> findById(UUID id) {
        // Soft Delete 처리된 사용자도 반환 - ADMIN이 status/deletedAt을 직접 확인
        return userJpaRepository.findById(id);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userJpaRepository.findByEmail(email);
    }

    @Override
    public Optional<User> findByKeycloakId(String keycloakId) {
        return userJpaRepository.findByKeycloakId(keycloakId);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userJpaRepository.existsByEmail(email);
    }
}
