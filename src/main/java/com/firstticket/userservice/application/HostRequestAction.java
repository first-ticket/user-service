package com.firstticket.userservice.application;

/**
 * HOST 신청 처리 액션
 * PATCH /api/v1/admin/host-requests/{requestId} 요청 바디에서 사용
 * Jackson이 "APPROVE" / "REJECT" 문자열을 자동으로 역직렬화
 *
 * domain이 아닌 application 위치 이유
 * - HTTP 요청 바디 (APPROVE/REJECT)를 Application 계층 파라미터로 변환
 * - domain의 상태 전이 규칙은 HostRequestStatus.validateNext()가 담당
 * - HostRequestAction을 domain에 두면 Jackson 직렬화 Presentation 관심사가 domain 레이어에 침투
 */
public enum HostRequestAction {
    APPROVE,
    REJECT
}
