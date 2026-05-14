-- V1: users 테이블 생성
-- User Aggregate 의 persistence 저장소
-- Flyway의 create-schemas: true 가 스키마를 생성하지만,
-- SQL 내에서도 명시적으로 보장하기 위해 IF NOT EXISTS 구문 사용
-- (멱등성 확보 - 이미 존재해도 오류 없이 통과)

CREATE SCHEMA IF NOT EXISTS user_schema;

CREATE TABLE user_schema.users
(
    -- PK: PostgreSQL 내장 함수 gen_random_uuid() 로 UUID v4 자동 생성
    id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,

    -- Keycloak 에서 발급된 사용자 sub(subject) UUID 문자열
    -- 로그인 토큰 검증 후 사용자 조회 시 사용
    keycloak_id VARCHAR(255) NOT NULL UNIQUE,

    -- 이메일 주소, Email VO 로 형식 검증 후 저장
    -- 대소문자 무관 중복 방지는 하단 uq_users_email_lower 인덱스가 담당
    email VARCHAR(255) NOT NULL,

    -- 사용자 이름
    username VARCHAR(100) NOT NULL,

    -- 역할: CUSTOMER / HOST / ADMIN (UserRole Enum 과 매핑)
    role VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER',

    -- 상태: ACTIVE / LOCKED / DELETED (UserStatus Enum 과 매핑)
    status VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',

    -- BaseEntity (Auditor TIMESTAMP 컬럼)
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP, -- 수정 전까지 null
    deleted_at TIMESTAMP, -- soft delete 시 기록

    -- BaseUserEntity (Auditor 사용자 식별 컬럼)
    -- SecurityAuditorAware 가 X-User-Id 헤더에서 추출하여 자동 주입
    created_by UUID NOT NULL,
    updated_by UUID,
    deleted_by UUID -- soft delete 주체
);

-- LOWER(email) 기준 unique index → 대소문자 무관 중복 방지
-- ex) test@email.com / Test@email.com는 동일한 이메일로 간주 및 처리
CREATE UNIQUE INDEX uq_users_email_lower ON user_schema.users (LOWER(email));
