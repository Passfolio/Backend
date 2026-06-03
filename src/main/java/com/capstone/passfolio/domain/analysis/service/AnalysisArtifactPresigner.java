package com.capstone.passfolio.domain.analysis.service;

import com.capstone.passfolio.domain.s3.service.S3Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;

/**
 * NONSTOP 핸드오프용 — 분석 결과 JSON의 cdn.passfolio.dev URL을 S3 presigned GET URL로 변환한다.
 *
 * <p>FastAPI가 {@code cdn.passfolio.dev/analyses/*.json}을 직접 fetch하면 우리 Cloudflare 봇 보호가
 * (캐시 안 되는 동적 JSON이라 매 요청 평가하여) FastAPI 서버의 데이터센터 IP를 403으로 막는다.
 * presigned S3 URL은 S3 엔드포인트 직결이라 Cloudflare/CloudFront를 거치지 않아 봇룰과 무관하게 받을 수 있다.
 *
 * <p>서명은 호출 시점의 SDK 자격증명(운영=EC2 역할)으로 이뤄지므로, 해당 역할이 분석 버킷에
 * {@code s3:GetObject} 권한을 가져야 한다. 변환 불가(미설정/파싱 실패/예외)는 fail-safe로 원본 cdnUrl을
 * 그대로 반환한다 — presign 문제가 핸드오프를 깨면 안 되고, 최악이라도 기존 동작(FastAPI 403→skip)으로 수렴.
 */
@Slf4j
@Component
public class AnalysisArtifactPresigner {

    private final S3Service s3Service;
    private final String outputBucket;
    private final Duration ttl;

    public AnalysisArtifactPresigner(
            S3Service s3Service,
            @Value("${analysis.artifact.output-bucket:}") String outputBucket,
            @Value("${analysis.artifact.presign-ttl-minutes:60}") long presignTtlMinutes) {
        this.s3Service = s3Service;
        this.outputBucket = outputBucket;
        this.ttl = Duration.ofMinutes(presignTtlMinutes);
    }

    /**
     * 분석 결과 cdnUrl → presigned S3 GET URL. 변환 불가 시 원본 cdnUrl 반환(fail-safe).
     * S3 key는 URL 경로를 그대로 사용한다(cdn 호스트 무관). 예: {@code https://cdn.passfolio.dev/analyses/1/x.json}
     * → key {@code analyses/1/x.json}.
     */
    public String presign(String cdnUrl) {
        if (cdnUrl == null || cdnUrl.isBlank()) {
            return cdnUrl;
        }
        if (outputBucket == null || outputBucket.isBlank()) {
            log.warn("[NONSTOP] analysis.artifact.output-bucket 미설정 → presign 생략, cdnUrl 그대로 사용");
            return cdnUrl;
        }
        try {
            String key = URI.create(cdnUrl).getPath().replaceFirst("^/", "");
            if (!key.startsWith("analyses/")) {
                log.warn("[NONSTOP] 예상 밖 분석 URL 경로 → presign 생략. cdnUrl={}", cdnUrl);
                return cdnUrl;
            }
            return s3Service.generateGetPresignedUrl(outputBucket, key, ttl);
        } catch (Exception e) {
            log.warn("[NONSTOP] presign 실패 → cdnUrl 그대로 사용. cdnUrl={}, err={}", cdnUrl, e.toString());
            return cdnUrl;
        }
    }
}
