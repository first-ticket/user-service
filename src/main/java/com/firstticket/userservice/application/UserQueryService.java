package com.firstticket.userservice.application;

import java.util.UUID;

import com.firstticket.userservice.application.dto.result.UserResult;
import com.firstticket.userservice.domain.User;
import com.firstticket.userservice.domain.UserRepository;
import com.firstticket.userservice.domain.UserStatus;
import com.firstticket.userservice.domain.exception.UserErrorCode;
import com.firstticket.userservice.domain.exception.UserException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 조회 전용 서비스 (CQRS Query Side)
 *
 * 설계 결정 사항
 * - UserCommandService와 UserQueryService를 별도 클래스로 분리하는 이유 :
 *   1. 읽기/쓰기 책임을 명확히 분리 → 가독성·유지보수성 향상
 *   2. 클래스 전체에 @Transactional(readOnly = true) 적용 → JPA dirty checking 비용 제거
 *   3. 향후 읽기 전용 DB 복제본 라우팅 등 확장 시 분리가 용이
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService {

    private final UserRepository userRepository;

    public UserResult getMyInfo(UUID keycloakId) {

        User user = userRepository.findByKeycloakId(keycloakId.toString())
            .orElseThrow(() -> {
                log.warn("[getMyInfo] 존재하지 않는 사용자 - keycloakId: {}", mask(keycloakId));
                return new UserException(UserErrorCode.USER_NOT_FOUND);
            });

        if (user.getStatus() == UserStatus.DELETED) {
            log.warn("[getMyInfo] 탈퇴한 사용자 조회 시도 - keycloakId: {}", mask(keycloakId));
            throw new UserException(UserErrorCode.USER_NOT_FOUND);
        }

        return UserResult.from(user);
    }

    /**
     * UUID 마스킹 - 운영 로그에서 사용자 식별자 원문 노출 방지
     * 앞 8자리와 뒤 12자리만 노출: xxxxxxxx-****-****-****-xxxxxxxxxxxx
     */
    private String mask(UUID uuid) {
        String s = uuid.toString(); // xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
        return s.substring(0, 8) + "-****-****-****-" + s.substring(24);
    }
}
