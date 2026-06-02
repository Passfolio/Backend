package com.capstone.passfolio.system.config.aws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

/**
 * SNS 클라이언트 빈(배치 완료 SMS). 자격증명 패턴은 SfnConfig/SqsConfig와 동일.
 */
@Configuration
public class SnsConfig {

    private static final Region REGION = Region.AP_NORTHEAST_2;

    @Value("${spring.cloud.aws.credentials.access-key:}")
    private String accessKey;

    @Value("${spring.cloud.aws.credentials.secret-key:}")
    private String secretKey;

    @Bean(destroyMethod = "close")
    public SnsClient snsClient() {
        var builder = SnsClient.builder().region(REGION);
        if (accessKey != null && !accessKey.isEmpty()
                && secretKey != null && !secretKey.isEmpty()) {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
            builder.credentialsProvider(StaticCredentialsProvider.create(credentials));
        }
        return builder.build();
    }
}
