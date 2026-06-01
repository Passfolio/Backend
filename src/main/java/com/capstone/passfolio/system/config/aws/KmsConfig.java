package com.capstone.passfolio.system.config.aws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;

/**
 * KMS 클라이언트 빈. GitHub 토큰을 Lambda로 전달하기 전 CMK로 암호화(kms:Encrypt)하는 데 사용.
 * Lambda가 동일 CMK + EncryptionContext로 복호(kms:Decrypt). 자격증명 패턴은 SfnConfig 등과 동일.
 */
@Configuration
public class KmsConfig {

    private static final Region REGION = Region.AP_NORTHEAST_2;

    @Value("${spring.cloud.aws.credentials.access-key:}")
    private String accessKey;

    @Value("${spring.cloud.aws.credentials.secret-key:}")
    private String secretKey;

    @Bean(destroyMethod = "close")
    public KmsClient kmsClient() {
        var builder = KmsClient.builder().region(REGION);
        if (accessKey != null && !accessKey.isEmpty()
                && secretKey != null && !secretKey.isEmpty()) {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
            builder.credentialsProvider(StaticCredentialsProvider.create(credentials));
        }
        return builder.build();
    }
}
