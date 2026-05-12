package com.capstone.passfolio.support;

import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 통합/E2E 테스트 공통 베이스.
 *
 * <p>책임:
 * <ul>
 *   <li>{@code @SpringBootTest} + {@code test} profile 활성화 → {@code application-test.yml} 로
 *       H2 / Flyway off / 모든 ${ENV} placeholder override 가 일괄 적용.</li>
 *   <li>외부 인프라 의존 빈을 일괄 mock — 컨텍스트 부팅이 Redis/S3 의 실가용성에 종속되지 않도록.
 *       프로젝트 어디서든 {@link RedissonClient} 를 주입받는 빈은 컨텍스트 로딩 시 즉시 Redis 에
 *       연결을 시도하므로 mock 이 필수.</li>
 * </ul>
 *
 * <p>도메인 고유 mock (예: {@code S3Service}) 또는 stubbing 은 각 테스트 클래스가 자신의
 * {@code @MockitoBean} / {@code @BeforeEach} 로 선언한다.
 *
 * <p>참고: {@code CareerDataInitializer} / {@code UniversityDataInitializer} 는 {@code @Profile("!test")}
 * 로 가드되어 본 베이스 클래스 사용 시 컨텍스트에 등록 자체가 되지 않는다 (mock 불필요).
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @MockitoBean
    protected RedissonClient redissonClient;

    @MockitoBean
    protected S3Client s3Client;

    @MockitoBean
    protected S3Presigner s3Presigner;
}
