package com.capstone.passfolio.common.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * S3 객체 키 → 클라이언트가 접근할 CDN URL로 변환.
 *
 * <p>설계 결정:
 * <ul>
 *   <li>{@code cdn.base-url} 프로퍼티 (이미 {@code application.yml}에 존재)를 통해 주입.
 *       빈 문자열이면 미설정으로 간주.</li>
 *   <li>{@code buildCdnUrl}은 정적 메서드로 노출 — 도메인 엔티티나 DTO 변환 헬퍼처럼
 *       Spring 컨텍스트 주입이 어려운 위치(예: {@code File} 엔티티 메서드)에서도 호출 가능.
 *       {@code @Value} setter가 클래스 로딩 시 한 번 주입되어 정적 필드를 채운다.</li>
 *   <li>CDN base URL이 미설정이면 명시적으로 {@link IllegalStateException}을 던진다.
 *       (file_security.md §5: S3 URL 직접 노출 차단 — CDN을 강제해 access-key 누출 방지.)</li>
 *   <li>마지막 슬래시는 자동 정규화 — 운영팀이 {@code https://cdn.x.com/} 또는
 *       {@code https://cdn.x.com} 어느 형태로 넣어도 결과 URL이 동일하도록.</li>
 * </ul>
 *
 * <p>설정 예: {@code cdn.base-url=https://cdn.passfolio.com}
 * <br>입력 키 {@code files/pdf/abc-uuid__resume.pdf}
 * <br>→ 출력 {@code https://cdn.passfolio.com/files/pdf/abc-uuid__resume.pdf}
 */
@Component
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class FileUrlUtils {

    private static String cdnBaseUrl;

    /**
     * Spring이 {@code cdn.base-url} 프로퍼티를 주입하면 trailing slash를 제거하고
     * 정적 필드에 보관한다. 프로퍼티가 비어 있으면 {@code null}로 둔다.
     */
    @Value("${cdn.base-url:#{null}}")
    public void setCdnBaseUrl(String cdnBaseUrl) {
        if (cdnBaseUrl == null || cdnBaseUrl.isBlank()) {
            FileUrlUtils.cdnBaseUrl = null;
            return;
        }
        FileUrlUtils.cdnBaseUrl = cdnBaseUrl.endsWith("/")
                ? cdnBaseUrl.substring(0, cdnBaseUrl.length() - 1)
                : cdnBaseUrl;
    }

    /**
     * S3 객체 키를 CDN URL로 변환한다.
     *
     * @param s3ObjectKey S3 object key (예: {@code files/pdf/uuid__filename.pdf}).
     *                    {@code null} 또는 빈 문자열이면 {@code null} 반환.
     * @return CDN URL (예: {@code https://cdn.passfolio.com/files/pdf/uuid__filename.pdf}).
     * @throws IllegalStateException {@code cdn.base-url}이 미설정인 경우.
     *         보안 정책상 S3 URL 직접 노출은 금지되므로 fail-fast 한다.
     */
    public static String buildCdnUrl(String s3ObjectKey) {
        if (s3ObjectKey == null || s3ObjectKey.isBlank()) {
            return null;
        }
        if (cdnBaseUrl == null || cdnBaseUrl.isBlank()) {
            throw new IllegalStateException(
                    "CDN base URL이 설정되지 않았습니다. 보안을 위해 CDN 설정이 필수입니다.");
        }
        String cleanPath = s3ObjectKey.startsWith("/")
                ? s3ObjectKey.substring(1)
                : s3ObjectKey;
        return cdnBaseUrl + "/" + cleanPath;
    }
}
