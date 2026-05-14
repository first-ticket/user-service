package com.firstticket.userservice.domain;

/**
 * First Ticket의 사용자 역할 (Role)
 * - CUSTOMER : 티켓 구매자 (신규 가입시 기본값)
 * - HOST : 공연 · 이벤트 주최자 (HostRequest 승인 후 별도로 부여)
 * - ADMIN : 시스템 관리자, 운영자
 */
public enum UserRole {
    CUSTOMER,
    HOST,
    ADMIN
}
