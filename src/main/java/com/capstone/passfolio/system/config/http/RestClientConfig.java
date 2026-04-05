package com.capstone.passfolio.system.config.http;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;

@Configuration
public class RestClientConfig {

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
}