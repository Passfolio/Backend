package com.capstone.passfolio.system.config.http;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;

@Configuration
public class RestClientConfig {

    public static final String GOV_DATA_REST_CLIENT = "govDataRestClient";
    public static final String AI_REST_CLIENT = "aiRestClient";

    @Bean
    public RestClient restClient() {
        // 1. Java 21+ HttpClient 사용 (Virtual Threads 친화적)
        HttpClient jdkHttpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2) // HTTP/2 지원
                .connectTimeout(Duration.ofSeconds(5)) // 연결 타임아웃 5초
                .executor(Executors.newVirtualThreadPerTaskExecutor()) // (선택) 내부 비동기 처리에 가상 스레드 사용
                .build();

        // 2. Spring Factory 어댑터 설정
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(jdkHttpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5)); // 읽기 타임아웃 5초

        // 3. RestClient 빌드
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * AI 서버 전용: AI 생성 작업이 길기 때문에 읽기 타임아웃을 60초로 설정한다.
     */
    @Bean
    @Qualifier(AI_REST_CLIENT)
    public RestClient aiRestClient(@Value("${ai.internal-api-key}") String internalApiKey) {
        HttpClient jdkHttpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(jdkHttpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        return RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader("X-INTERNAL-API-KEY", internalApiKey)
                .build();
    }

    /**
     * 공공데이터포털 등 외부 공공 API용: 연결·읽기 타임아웃을 조금 넉넉히 둔다.
     */
    @Bean
    @Qualifier(GOV_DATA_REST_CLIENT)
    public RestClient govDataRestClient() {
        HttpClient jdkHttpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(3))
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(jdkHttpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}