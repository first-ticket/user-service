package com.firstticket.userservice.infrastructure.provider;

import java.net.http.HttpClient;
import java.time.Duration;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Keycloak Admin Client Bean 설정
 *
 * 설계 결정 사항
 * - Keycloak 객체는 Singleton 빈으로 등록 → HTTP 연결 풀 재사용, 토큰 캐싱 자동 처리
 * - 매 요청마다 새로 객체를 생성하면 인증 토큰 발급 비용이 반복적으로 발생하므로 빈으로 관리
 * - @EnableConfigurationProperties: KeycloakProperties를 본 Configuration 클래스에서만 활성화
 */
@Configuration
@EnableConfigurationProperties(KeycloakProperties.class) // KeycloakProperties 빈 등록 활성화
public class KeycloakConfig {

    /**
     * Keycloak Admin REST API 호출을 위한 admin 클라이언트 빈
     *
     * - serverUrl: Keycloak 서버 주소
     * - realm("master"): Admin REST API 는 항상 master realm 에서 인증
     * - clientId("admin-cli"): Keycloak 기본 내장 관리용 클라이언트 ID
     * - username/password: application.yaml의 admin 자격증명
     */
    @Bean
    public Keycloak keycloakAdminClient(KeycloakProperties properties) {
        return KeycloakBuilder.builder()
            .serverUrl(properties.serverUrl())
            .realm("master") // Admin API 인증은 항상 master realm
            .clientId("admin-cli") // Keycloak 기본 관리용 클라이언트
            .username(properties.adminUsername())
            .password(properties.adminPassword())
            .build();
    }

    /**
     * Keycloak Token Endpoint 호출용 RestClient 빈
     *
     * 설계 결정 사항
     * - connectTimeout 3초: 동일 VPC/네트워크 내 Keycloak 서버 연결 지연 임계값
     * - readTimeout 5초: Keycloak 토큰 발급 처리 시간 + 네트워크 왕복 포함 임계값
     * - 운영 환경 모니터링 후 조정 가능
     */
    @Bean
    public RestClient keycloakRestClient() {
        // 연결 타임아웃 설정 (서버 연결 수립 단계)
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

        // 읽기 타임아웃 추가 설정 (응답 수신 단계)
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        return RestClient.builder()
            .requestFactory(requestFactory)
            .build();
    }
}
