package com.capstone.passfolio.system.config.aws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * AWS S3 클라이언트 / Presigner 빈 등록.
 *
 * <p>Direct-to-S3 멀티파트 업로드(presigned URL 방식)를 위해 두 개의 빈을 노출한다:
 * <ul>
 *   <li>{@link S3Client} — {@code createMultipartUpload}, {@code listParts},
 *       {@code completeMultipartUpload}, {@code abortMultipartUpload},
 *       {@code headObject} 호출 (서버에서 직접 S3 제어 평면 호출)</li>
 *   <li>{@link S3Presigner} — {@code UploadPartRequest} 에 대한 단기 presigned PUT URL
 *       발급 (클라이언트가 S3에 직접 part 데이터 PUT)</li>
 * </ul>
 *
 * <p>리전은 {@code spring.cloud.aws.region.static} 와 동일한 {@code ap-northeast-2} 로
 * 고정한다 (이미 {@link SfnConfig} 도 같은 리전 상수를 사용 — 단일 AWS 계정·리전 정책).
 *
 * <p>자격증명 분기:
 * <ul>
 *   <li>{@code spring.cloud.aws.credentials.access-key/secret-key} 가 모두 비어있지
 *       않으면 → {@link StaticCredentialsProvider} (개발/로컬)</li>
 *   <li>둘 중 하나라도 비어있으면 → SDK 기본 자격증명 체인 (환경변수 / 프로파일 /
 *       EC2 IAM Role / ECS Task Role) — 운영 권장</li>
 * </ul>
 *
 * <p>{@code SfnConfig} 와 동일한 분기 로직을 따라 일관성 유지. {@code @Bean} 의
 * {@code destroyMethod = "close"} 옵션으로 컨텍스트 종료 시 SDK 내부 HTTP 클라이언트가
 * 깨끗하게 닫히도록 한다 (file_security.md 의 "리소스 누수" 항목 대응).
 */
@Configuration
public class S3PresignerConfig {

    private static final Region REGION = Region.AP_NORTHEAST_2;

    @Value("${spring.cloud.aws.credentials.access-key:}")
    private String accessKey;

    @Value("${spring.cloud.aws.credentials.secret-key:}")
    private String secretKey;

    /**
     * 서버가 직접 호출하는 S3 제어 평면 클라이언트.
     *
     * <p>멀티파트 업로드의 initiate / list-parts / complete / abort 와
     * 사후 크기 검증용 headObject 에서 사용된다.
     */
    @Bean(destroyMethod = "close")
    public S3Client s3Client() {
        var builder = S3Client.builder().region(REGION);
        applyCredentialsIfPresent(builder::credentialsProvider);
        return builder.build();
    }

    /**
     * 클라이언트가 part 를 PUT 할 때 사용하는 단기 presigned URL 발급기.
     *
     * <p>{@code presigned-url-ttl-minutes} (기본 10분) 만료. {@code Content-Length}
     * 와 (옵션) {@code Content-MD5} 를 서명에 결속하여 변조·DoS 를 방지한다.
     */
    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner() {
        var builder = S3Presigner.builder().region(REGION);
        applyCredentialsIfPresent(builder::credentialsProvider);
        return builder.build();
    }

    /**
     * access-key / secret-key 가 둘 다 비어있지 않을 때만 명시적 자격증명을 적용.
     * 그 외에는 SDK 기본 체인이 처리하도록 그대로 둔다.
     *
     * <p>{@link S3Client.Builder} 와 {@link S3Presigner.Builder} 가 공통 인터페이스를
     * 공유하지 않아 setter 함수 참조를 받아서 양쪽에 적용한다.
     */
    private void applyCredentialsIfPresent(
            java.util.function.Consumer<software.amazon.awssdk.auth.credentials.AwsCredentialsProvider> setter) {
        if (accessKey != null && !accessKey.isEmpty()
                && secretKey != null && !secretKey.isEmpty()) {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
            setter.accept(StaticCredentialsProvider.create(credentials));
        }
    }
}
