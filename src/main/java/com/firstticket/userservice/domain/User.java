package com.firstticket.userservice.domain;

import com.firstticket.common.persistence.BaseUserEntity;
import com.firstticket.userservice.domain.exception.UserErrorCode;
import com.firstticket.userservice.domain.exception.UserException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * User Aggregate Root Entity
 *
 * 설계 결정 사항
 * - BaseUserEntity 상속 : Auditor 자동 관리
 * - keycloak_id : Keycloak 에 생성된 사용자의 sub(subject) UUID (로그인 토큰 검증 시 사용)
 * - email : Email VO 로 검증 후 String 값만 DB에 저장
 * - @Embeddable record 는 Hibernate 6 호환성 이슈가 있어 String 직접 저장 방식 채택
 * - Soft Delete 정책 : softDelete(UUID) 호출 시 status = DELETED + deletedAt/deletedBy 기록
 * - @NoArgsConstructor(PROTECTED) : JPA 리플렉션용 기본 생성자를 외부에서 직접 호출하지 못하게 제한
 * - 정적 팩토리 메서드 create() 를 유일한 생성 진입점으로 강제
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 전용 기본 생성자 (외부 사용 금지)
@AllArgsConstructor(access = AccessLevel.PRIVATE) // create() 팩토리 메서드 전용 생성자
public class User extends BaseUserEntity {

    //PK
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Keycloak 사용자 ID (sub claim)
     * Keycloak 의 Access Token 에 포함된 sub 값과 1:1 매핑
     * 한 번 설정되면 변경 불가 (updatable = false)
     */
    @Column(name = "keycloak_id", nullable = false, unique = true, updatable = false)
    private String keycloakId;

    /**
     * 이메일 주소 - Email VO 의 유효성 검증을 통과한 값만 저장됨
     * 로그인 시도시 비밀번호와 함께 사용됩니다.
     * 중복 가입 방지를 위해 unique 제약조건 적용
     */
    @Column(name = "email", nullable = false)
    private String email;

    /** 사용자 표시 이름 */
    @Column(name = "username", nullable = false)
    private String username;

    /**
     * 사용자 역할 권한 - 문자열로 저장(EnumType.STRING)
     * 기본값은 CUSTOMER, HostRequest 승인 시 HOST 로 변경됨
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role;

    /**
     * 계정 상태 status - 문자열로 저장 (EnumType.STRING)
     * 상태 전이는 반드시 UserStatus.validateNext() 를 통해서만 수행되어야 함
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    /**
     * User 생성 팩토리 메서드
     * - @AllArgsConstructor(PRIVATE)로 생성된 private 생성자를 사용
     * - role 은 CUSTOMER 로 고정 (최초 가입 시 항상 일반 사용자)
     * - status 는 ACTIVE 로 고정
     * - email 은 Email VO 를 받아 .value() 로 String 추출 -> 이미 검증된 값 보장
     */
    public static User create(String keycloakId, Email email, String username) {
        return new User(
            null,
            keycloakId,
            email.value(),
            username,
            UserRole.CUSTOMER,
            UserStatus.ACTIVE
        );
    }

    // 비즈니스 메서드

    /**
     * 계정 잠금
     * UserStatus.ACTIVE.validateNext(LOCKED) 를 통해 전이 가능 여부를 먼저 확인합니다.
     */
    public void lock() {
        this.status.validateNext(UserStatus.LOCKED); // 전이 불가 시 BusinessException 던짐
        this.status = UserStatus.LOCKED;
    }

    /**
     * 잠금상태 해제
     * LOCKED 상태에서만 호출 가능
     */
    public void unlock() {
        this.status.validateNext(UserStatus.ACTIVE);
        this.status = UserStatus.ACTIVE;
    }

    /**
     * Sofe Delete 처리
     * status = DELETED 로 변경 + BaseUserEntity.delete() 로 deletedAt/deletedBy 기록
     * DELETED 는 최종 상태이므로 이후 어떤 상태 전이도 불가능합니다.
     * 추후 삭제 사용자의 재가입 관련 비즈니즈 룰이 확보되면 조정 및 추가 개발
     */
    public void softDelete(UUID deletedBy) {
        this.status.validateNext(UserStatus.DELETED); // 이미 DELETED 이면 예외 발생
        this.status = UserStatus.DELETED;
        super.delete(deletedBy); // BaseUserEntity: deletedAt = now(), deletedBy = deletedBy
    }

    /**
     * 사용자 역할 role 변경
     * 주로 HostRequest 승인 시 CUSTOMER → HOST 로 변경하거나
     * 관리자가 직접 역할을 조정할 때 사용합니다.
     */
    public void changeRole(UserRole newRole) {
        if (this.status == UserStatus.DELETED) {
            throw new UserException(UserErrorCode.USER_ALREADY_DELETED);
        }
        this.role = newRole;
    }

    /**
     * 사용자 이름 변경
     */
    public void changeUsername(String newUsername) {
        this.username = newUsername;
    }

    /**
     * 회원가입 등 인증 컨텍스트가 없는 요청에서는 JPA Auditing이 created_by를 채울 수 없습니다.
     * 이 메서드는 Keycloak에서 발급한 자신의 UUID를 created_by로 명시적으로 주입합니다.
     */
    public void initCreatedBy(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("createdBy는 null값을 허용하지 않습니다.");
        }
        if (this.createdBy != null) {
            throw new IllegalStateException("createdBy는 이미 초기화되서 변경할 수 없습니다.");
        }
        this.createdBy = id;
    }

    /**
     * 사용자 정보 수정
     *
     * - DELETED 상태의 사용자는 수정 불가
     * - 향후 수정 가능 필드가 늘어나면 UpdateProfileCommand 형태로 파라미터 확장 가능
     */
    public void updateProfile(String newUsername) {
        if (this.status == UserStatus.DELETED) {
            throw new UserException(UserErrorCode.USER_ALREADY_DELETED);
        }
        this.username = newUsername;
    }
}
