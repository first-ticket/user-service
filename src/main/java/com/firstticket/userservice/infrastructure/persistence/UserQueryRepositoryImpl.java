package com.firstticket.userservice.infrastructure.persistence;

import com.firstticket.userservice.domain.QUser;
import com.firstticket.userservice.domain.query.UserQueryRepository;
import com.firstticket.userservice.domain.query.UserSearchSpec;
import com.firstticket.userservice.domain.query.UserSummaryData;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * UserQueryRepository 의 Querydsl 구현체
 *
 * 설계 결정 사항
 * - JPAQueryFactory 는 common 모듈의 JpaConfig 에서 빈으로 등록됨 → 주입받아서 사용
 * - QUser 는 @Entity User 에서 Querydsl APT가 자동 생성 (./gradlew compileJava 후 생성)
 * - BooleanBuilder 로 동적 조건을 조합 → null 조건은 자동으로 무시 처리
 * - Projections.constructor() 로 UserSummaryData record 의 canonical constructor 를 호출
 *   → 필드 선언 순서와 일치해야 함 (id, email, username, role, status, createdAt)
 */
@Repository
@RequiredArgsConstructor
public class UserQueryRepositoryImpl implements UserQueryRepository {

    // JpaConfig 에서 등록한 JPAQueryFactory 빈 주입
    private final JPAQueryFactory queryFactory;

    @Override
    public Page<UserSummaryData> searchUsers(UserSearchSpec spec, Pageable pageable) {
        QUser user = QUser.user; // Querydsl APT가 생성한 Q 타입

        // 동적 조건 조합 - spec 필드가 null 이면 해당 조건 무시
        BooleanBuilder predicate = buildPredicate(spec, user);

        // 데이터 조회 쿼리
        List<UserSummaryData> content = queryFactory
            .select(Projections.constructor(
                UserSummaryData.class, // record 의 canonical constructor 사용
                user.id,
                user.email,
                user.username,
                user.role,
                user.status,
                user.createdAt
            ))
            .from(user)
            .where(predicate)
            .where(user.deletedAt.isNull()) // Soft Deleted 사용자 제외
            .orderBy(user.createdAt.desc()) // 최신 가입순 정렬
            .offset(pageable.getOffset()) // 페이지 시작 offset
            .limit(pageable.getPageSize()) // 페이지 size
            .fetch();

        // 전체 건수 조회 (페이징 정보 계산용) - count 쿼리 별도 실행
        Long total = queryFactory
            .select(user.count())
            .from(user)
            .where(predicate)
            .where(user.deletedAt.isNull())
            .fetchOne();

        // total이 null이면 0으로 처리 (결과 없는 케이스)
        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }

    @Override
    public Optional<UserSummaryData> findSummaryById(UUID id) {
        QUser user = QUser.user;

        // id 일치 + Soft Deleted 제외 조건으로 단건 조회
        UserSummaryData data = queryFactory
            .select(Projections.constructor(
                UserSummaryData.class,
                user.id,
                user.email,
                user.username,
                user.role,
                user.status,
                user.createdAt
            ))
            .from(user)
            .where(
                user.id.eq(id), // PK 조건
                user.deletedAt.isNull() // Soft Deleted 제외
            )
            .fetchOne(); // 결과 없으면 null 반환

        return Optional.ofNullable(data);
    }

    /**
     * UserSearchSpec의 각 필드를 BooleanBuilder에 조건으로 추가
     * null 필드는 추가하지 않으므로 "조건 없음 = 전체 검색" 이 자동으로 처리
     */
    private BooleanBuilder buildPredicate(UserSearchSpec spec, QUser user) {
        BooleanBuilder builder = new BooleanBuilder();

        // 이메일 부분 일치 검색 (대소문자 무시)
        if (spec.email() != null && !spec.email().isBlank()) {
            builder.and(user.email.containsIgnoreCase(spec.email()));
        }

        // username 부분 일치 검색 (대소문자 무시)
        if (spec.username() != null && !spec.username().isBlank()) {
            builder.and(user.username.containsIgnoreCase(spec.username()));
        }

        // role 필터
        if (spec.role() != null) {
            builder.and(user.role.eq(spec.role()));
        }

        // status 필터
        if (spec.status() != null) {
            builder.and(user.status.eq(spec.status()));
        }

        return builder;
    }
}
