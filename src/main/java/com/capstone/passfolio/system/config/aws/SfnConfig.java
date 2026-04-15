package com.capstone.passfolio.system.config.aws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sfn.SfnClient;

@Configuration
public class SfnConfig {

    private static final Region REGION = Region.AP_NORTHEAST_2;

    @Value("${spring.cloud.aws.credentials.access-key:}")
    private String accessKey;

    @Value("${spring.cloud.aws.credentials.secret-key:}")
    private String secretKey;

    @Bean
    public SfnClient sfnClient() {
        var builder = SfnClient.builder().region(REGION);

        // 개발 환경 등에서 명시적 자격 증명을 제공하면 static provider 사용
        if (accessKey != null && !accessKey.isEmpty()
                && secretKey != null && !secretKey.isEmpty()) {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
            builder.credentialsProvider(StaticCredentialsProvider.create(credentials));
        }

        // 자격 증명이 없으면 SDK 기본 체인(환경 변수/프로파일/IAM Role) 사용
        return builder.build();
    }
}
