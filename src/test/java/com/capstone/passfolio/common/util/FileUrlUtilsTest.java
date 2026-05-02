package com.capstone.passfolio.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link FileUrlUtils} 단위 테스트.
 *
 * <p>SUT 는 {@code @Value} setter 가 정적 필드를 채우는 패턴이라 Spring 컨텍스트 없이는
 * 정적 필드가 비어 있다. 따라서 각 테스트가 자기 시나리오에 맞게 setter 를 직접 호출하거나
 * {@link ReflectionTestUtils} 로 정적 필드를 비우고 시작한다. 테스트 간 누수 방지를 위해
 * {@code @AfterEach} 에서 정적 필드를 항상 {@code null} 로 리셋.
 *
 * <p><b>검증 명세</b>:
 * <ul>
 *   <li>{@code setCdnBaseUrl} — null/blank/trailing-slash 정규화 동작.</li>
 *   <li>{@code buildCdnUrl} — happy path, leading-slash key 정규화, null/blank key 단축 반환,
 *       CDN 미설정 시 fail-fast (file_security.md §5: S3 URL 직접 노출 차단).</li>
 * </ul>
 */
class FileUrlUtilsTest {

    @AfterEach
    void resetStaticState() {
        // 다음 테스트가 setter 를 호출하지 않을 수 있으므로 항상 비우고 시작하도록.
        ReflectionTestUtils.setField(FileUrlUtils.class, "cdnBaseUrl", null);
    }

    @Nested
    @DisplayName("setCdnBaseUrl(): @Value 주입 setter 정규화 규칙")
    class SetCdnBaseUrl {

        @Test
        @DisplayName("정상 URL 주입 → 정적 필드에 그대로 저장")
        void normalUrl_storedAsIs() {
            FileUrlUtils utils = new FileUrlUtils();
            utils.setCdnBaseUrl("https://cdn.passfolio.com");

            String stored = (String) ReflectionTestUtils.getField(FileUrlUtils.class, "cdnBaseUrl");
            assertThat(stored).isEqualTo("https://cdn.passfolio.com");
        }

        @Test
        @DisplayName("trailing slash 가 있는 URL → slash 제거 후 저장 (운영팀 입력 형식 흡수)")
        void trailingSlash_isStripped() {
            FileUrlUtils utils = new FileUrlUtils();
            utils.setCdnBaseUrl("https://cdn.passfolio.com/");

            String stored = (String) ReflectionTestUtils.getField(FileUrlUtils.class, "cdnBaseUrl");
            assertThat(stored).isEqualTo("https://cdn.passfolio.com");
        }

        @Test
        @DisplayName("null 주입 → 정적 필드 null (미설정과 동일)")
        void nullInput_storesNull() {
            // 먼저 어떤 값을 채워두어야 null 로 덮어씌우는 동작을 검증할 수 있음
            ReflectionTestUtils.setField(FileUrlUtils.class, "cdnBaseUrl", "stale-value");
            new FileUrlUtils().setCdnBaseUrl(null);

            assertThat((String) ReflectionTestUtils.getField(FileUrlUtils.class, "cdnBaseUrl"))
                    .isNull();
        }

        @Test
        @DisplayName("blank (whitespace only) 주입 → 정적 필드 null (보안 정책상 미설정으로 간주)")
        void blankInput_storesNull() {
            ReflectionTestUtils.setField(FileUrlUtils.class, "cdnBaseUrl", "stale-value");
            new FileUrlUtils().setCdnBaseUrl("   ");

            assertThat((String) ReflectionTestUtils.getField(FileUrlUtils.class, "cdnBaseUrl"))
                    .isNull();
        }
    }

    @Nested
    @DisplayName("buildCdnUrl(): CDN URL 조립 + 입력 정규화 + 보안 fail-fast")
    class BuildCdnUrl {

        @Test
        @DisplayName("happy path: cdnBase + '/' + key 결합")
        void happyPath_concatsBaseAndKey() {
            new FileUrlUtils().setCdnBaseUrl("https://cdn.passfolio.com");

            String url = FileUrlUtils.buildCdnUrl("files/uuid__resume.pdf");

            assertThat(url).isEqualTo("https://cdn.passfolio.com/files/uuid__resume.pdf");
        }

        @Test
        @DisplayName("leading-slash 가 붙은 key → 한 번만 '/' 사이에 끼도록 정규화")
        void leadingSlashKey_isNormalized() {
            new FileUrlUtils().setCdnBaseUrl("https://cdn.passfolio.com");

            String url = FileUrlUtils.buildCdnUrl("/files/uuid__resume.pdf");

            // 결과에 '//' 가 들어가면 안 됨
            assertThat(url).isEqualTo("https://cdn.passfolio.com/files/uuid__resume.pdf");
            assertThat(url).doesNotContain("com//");
        }

        @Test
        @DisplayName("setter 가 trailing-slash 를 제거하기 때문에 입력 base 가 'cdn.x.com/' 이어도 결과는 동일")
        void trailingSlashBase_producesSameUrl() {
            new FileUrlUtils().setCdnBaseUrl("https://cdn.passfolio.com/");

            String url = FileUrlUtils.buildCdnUrl("files/k");

            assertThat(url).isEqualTo("https://cdn.passfolio.com/files/k");
        }

        @Test
        @DisplayName("null key → null 반환 (no exception)")
        void nullKey_returnsNull() {
            new FileUrlUtils().setCdnBaseUrl("https://cdn.passfolio.com");

            assertThat(FileUrlUtils.buildCdnUrl(null)).isNull();
        }

        @Test
        @DisplayName("blank key → null 반환 (no exception)")
        void blankKey_returnsNull() {
            new FileUrlUtils().setCdnBaseUrl("https://cdn.passfolio.com");

            assertThat(FileUrlUtils.buildCdnUrl("   ")).isNull();
        }

        @Test
        @DisplayName("CDN 미설정 (null) + 정상 key → IllegalStateException — file_security.md §5 fail-fast")
        void cdnNotSet_throws() {
            // setter 를 호출하지 않음 → 정적 필드 null
            assertThatThrownBy(() -> FileUrlUtils.buildCdnUrl("files/k"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CDN");
        }

        @Test
        @DisplayName("CDN 이 빈 문자열로 강제 주입된 상태에서도 IllegalStateException (방어적 가드)")
        void cdnBlank_throws() {
            ReflectionTestUtils.setField(FileUrlUtils.class, "cdnBaseUrl", "");

            assertThatThrownBy(() -> FileUrlUtils.buildCdnUrl("files/k"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
