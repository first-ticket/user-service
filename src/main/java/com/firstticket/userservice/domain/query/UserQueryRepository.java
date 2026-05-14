package com.firstticket.userservice.domain.query;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

/**
 * 사용자 조회 전용(CQRS) Repository 인터페이스
 *
 * 설계 결정 사항
 * - UserRepository(Command) 와 분리하여 읽기/쓰기 책임을 명확히 구분
 * - domain 계층에 선언, infrastructure/persistence/UserQueryRepositoryImpl 에서 Querydsl 로 구현 (동적 다중 조건 검색)
 * - Pageable 을 직접 받아 페이징/정렬 지원
 */
public interface UserQueryRepository {

    /**
     * ADMIN전용 사용자 목록 검색
     * UserSearchSpec 의 각 조건은 null인 경우 해당 필터를 적용하지 않음
     * Soft Deleted 처리된 사용자(deletedAt IS NOT NULL)는 기본적으로 제외
     */
    Page<UserSummaryData> searchUsers(UserSearchSpec spec, Pageable pageable);

    /**
     * 단건 사용자 요약 조회
     * Soft Deleted 처리된 사용자(deletedAt IS NOT NULL)는 기본적으로 제외
     */
    Optional<UserSummaryData> findSummaryById(UUID id);
}
