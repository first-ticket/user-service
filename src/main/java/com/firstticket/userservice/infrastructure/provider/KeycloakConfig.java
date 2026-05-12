package com.firstticket.userservice.infrastructure.provider;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Keycloak Admin Client Bean 설정
 *
 * 설계 결정 사항
 * - Keycloak 객체는 Singleton 빈으로 등록 → HTTP 연결 풀 재사용, 토큰 캐싱 자동 처리
 * - 매 요청마다 새로 객체를 생성하면 인증 토큰 발급 비용이 반복적으로 발생하므로 빈으로 관리
 * - keycloakRestClient: JDK HttpClient -> Apache HttpClient 5로 교체
 *   사유: JDK HttpClient는 커넥션 풀 크기를 외부에서 제어할 수 없어
 *         동시 요청이 몰릴 때 연결 대기 발생. Apache HttpClient 5는
 *         PoolingHttpClientConnectionManager로 명시적 풀 제어 가능
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
     * - Apache HttpClient 5 + PoolingHttpClientConnectionManager 사용
     *   → maxTotal(100): 전체 커넥션 풀 크기
     *   → maxPerRoute(50): Keycloak 단일 호스트에 대한 최대 동시 커넥션
     *     (부하테스트 최대 vuser 수 이상으로 설정하여 커넥션 대기 제거)
     *   → 초과 시 즉시 실패로 처리하여 대기 누적 방지
     * - connectTimeout(3s): 동일 네트워크 내 Keycloak 서버 연결 타임아웃
     * - responseTimeout(5s): Keycloak 토큰 발급 응답 수신 타임아웃
     */
    @Bean
    public RestClient keycloakRestClient() {
        // 커넥션 풀: Keycloak은 단일 호스트이므로 maxPerRoute를 넉넉하게 설정
        PoolingHttpClientConnectionManager connectionManager =
            new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(100); // 전체 최대 커넥션 수
        connectionManager.setDefaultMaxPerRoute(50); // 단일 호스트(Keycloak)당 최대 커넥션 수

        // 요청별 타임아웃 설정
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofSeconds(1)) // 풀에서 커넥션 획득 대기 상한
            .setConnectTimeout(Timeout.ofSeconds(3)) // 연결 수립 타임아웃 (Apache HttpClient 5 방식)
            .setResponseTimeout(Timeout.ofSeconds(5)) // 응답 수신 타임아웃
            .build();

        // 커넥션 풀과 타임아웃을 적용한 HttpClient 생성
        CloseableHttpClient httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .build();

        // Spring RestClient에 Apache HttpClient 5 기반 팩토리 주입
        // 기존 setConnectTimeout(int) 호출 제거 > 타임아웃은 RequestConfig에서 이미 설정됨
        HttpComponentsClientHttpRequestFactory requestFactory =
            new HttpComponentsClientHttpRequestFactory(httpClient);

        return RestClient.builder()
            .requestFactory(requestFactory)
            .build();
    }
}
