package com.firstticket.userservice.domain;

import com.firstticket.common.persistence.BaseUserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Host 신청 Aggregate Root Entity
 *
 * 설계 결정 사항
 * - user_id 는 @ManyToOne 대신 UUID를 직접 참조 → MSA 환경에서 cross-aggregate JPA 조인을 피해 경계를 명확하게 함
 * - (다른 Aggregate 는 ID 로만 참조한다는 DDD 원칙)
 * - approve() / reject() 호출 시 HostRequestStatus 전이 유효성을 스스로 검증
 * - 한 사용자는 동시에 PENDING 상태 신청을 하나만 가질 수 있습니다
 *   (중복 방지는 HostRequestRepository.existsByUserIdAndStatus() 로 Application 계층에서 확인합니다.)
 */
@Entity
@Table(name = "host_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 전용 기본 생성자
public class HostRequest extends BaseUserEntity {

    // PK
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * 신청한 사용자의 UUID (FK -> users.id)
     * @ManyToOne 대신 UUID 직접 저장 - Aggregate 경계
     */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /**
     * HOST 신청 상태
     * 초기값은 PENDING, approve()/reject() 를 통해서만 상태 변경 가능합니다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private HostRequestStatus status;

    // 정적 팩토리 메서드
    /**
     * HostRequest 생성 팩토리 메서드
     * status 는 항상 PENDING 으로 초기화됩니다.
     */
    public static HostRequest create(UUID userId) {
        HostRequest request = new HostRequest();
        request.userId = userId;
        request.status = HostRequestStatus.PENDING; // 초기 상태는 PENDING으로 고정
        return request;
    }

    // 비즈니스 메서드
    /**
     * 호스트 신청 승인
     * - PENDING → APPROVED 전이 (다른 상태에서 호출 시 BusinessException 던짐)
     * - 호출 이후 Application 계층에서 User.changeRole(HOST) 를 연계 호출해야 합니다.
     */
    public void approve() {
        this.status.validateNext(HostRequestStatus.APPROVED); // 전이 가능 여부 검증 메서드
        this.status = HostRequestStatus.APPROVED;
    }

    /**
     * 호스트 신청 거절
     * - PENDING → REJECTED 전이
     */
    public void reject() {
        this.status.validateNext(HostRequestStatus.REJECTED);
        this.status = HostRequestStatus.REJECTED;
    }

    /**
     * Soft Delete
     * BaseUserEntity.delete() 로 deletedAt/deletedBy 만 기록
     * status 는 변경하지 않습니다 - 이력 보존
     */
    public void softDelete(UUID deletedBy) {
        super.delete(deletedBy); // deletedAt = now(), deletedBy = deletedBy
    }
}
