package com.capstone.passfolio.system.config.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * FileUploadProperties 단위 테스트.
 *
 * Spring 컨텍스트 0개 — 일반 POJO처럼 인스턴스화한 뒤 {@code normalize()}를 직접 호출해
 * @PostConstruct 가 했어야 할 정규화를 재현한다. 같은 패키지에 있으므로 package-private
 * normalize() 호출 가능.
 *
 * 검증 명세:
 *  1. 정규화: 점/공백/대문자가 섞여 들어와도 비교용 Set 은 항상 dot-stripped + lowercase + trimmed.
 *  2. blank/null 항목은 화이트리스트에서 제거된다 (정규화 단계에서 필터링).
 *  3. isExtensionAllowed 의 분기:
 *     - 화이트리스트가 비어있으면 → 항상 통과 (운영 결정으로 해석).
 *     - filename 이 null/blank → 거부 (비어있는 입력은 통과할 수 없음).
 *     - 확장자가 없는 파일 (점 없거나 점이 마지막 char) → allowFilesWithNoExtension 분기.
 *     - 확장자가 있는 파일 → 대소문자 무관하게 화이트리스트와 비교.
 */
class FileUploadPropertiesTest {

    /**
     * 테스트 헬퍼: setter 호출 후 @PostConstruct 가 했어야 할 normalize() 를 강제 호출.
     * 같은 패키지에 있으므로 package-private 메서드 직접 호출 가능.
     */
    private static FileUploadProperties propsWith(List<String> allowedExtensions, boolean allowNoExt) {
        FileUploadProperties props = new FileUploadProperties();
        props.setAllowedExtensions(allowedExtensions);
        props.setAllowFilesWithNoExtension(allowNoExt);
        props.normalize();
        return props;
    }

    @Nested
    @DisplayName("기본값 (생성자만 호출)")
    class Defaults {

        @Test
        @DisplayName("백로그 제약값으로 초기화: 100MB, 10분 TTL, 15개 확장자, no-extension 거부")
        void defaultValues_matchBacklogConstraints() {
            FileUploadProperties props = new FileUploadProperties();

            // 백로그 핵심 제약: 100MB = 104857600 bytes
            assertThat(props.getMaxFileSizeBytes()).isEqualTo(104857600L);
            // TTL 기본 10분 (file_security.md §3 short TTL 권장)
            assertThat(props.getPresignedUrlTtlMinutes()).isEqualTo(10);
            // 확장자 없는 파일은 기본 거부
            assertThat(props.isAllowFilesWithNoExtension()).isFalse();
            // 백로그 화이트리스트 = pdf + 이미지 7종 + 비디오 7종 = 15
            assertThat(props.getAllowedExtensions())
                    .hasSize(15)
                    .contains("pdf", "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg",
                              "mp4", "mov", "webm", "avi", "wmv", "flv", "mkv");
        }
    }

    @Nested
    @DisplayName("normalize(): 정규화 규칙")
    class Normalize {

        @Test
        @DisplayName("대소문자 + 점 + 공백 섞여도 normalized set 은 dot-stripped lowercase trim")
        void normalize_stripsDotsLowercasesAndTrims() {
            FileUploadProperties props = propsWith(
                    Arrays.asList(".PDF", "  JPG  ", ".png", "MP4"),
                    false
            );

            // 비교용 Set 은 정규화된 형태로만 들어가야 한다 — getter 가 노출돼 있지 않으므로
            // 행위(isExtensionAllowed) 로 검증한다.
            assertThat(props.isExtensionAllowed("doc.pdf")).isTrue();    // ".PDF" → "pdf"
            assertThat(props.isExtensionAllowed("photo.jpg")).isTrue();  // " JPG " → "jpg"
            assertThat(props.isExtensionAllowed("img.png")).isTrue();    // ".png" → "png"
            assertThat(props.isExtensionAllowed("clip.mp4")).isTrue();   // "MP4" → "mp4"
        }

        @Test
        @DisplayName("null/blank 항목은 정규화 단계에서 제거된다")
        void normalize_dropsNullAndBlankEntries() {
            FileUploadProperties props = propsWith(
                    Arrays.asList("pdf", null, "", "  ", "png"),
                    false
            );

            assertThat(props.isExtensionAllowed("a.pdf")).isTrue();
            assertThat(props.isExtensionAllowed("b.png")).isTrue();
            // "" / "  " / null 이 정규화에 살아남아 매칭되면 안 됨 — 즉 빈 문자열을
            // 확장자로 가진 가상의 파일이 통과되면 안 됨. 실제로는 .lastIndexOf 가
            // 처리해 다른 분기로 빠지므로 직접적인 우회는 불가하지만,
            // "extensionless 파일이 false 인데 normalized Set 안에 ''가 있어
            // contains('') == true" 같은 가짜 매칭이 일어나지 않는지를 다음 테스트로 본다.
            assertThat(props.isExtensionAllowed("noext")).isFalse();
        }
    }

    @Nested
    @DisplayName("isExtensionAllowed(): 화이트리스트 매칭")
    class WhitelistMatching {

        @Test
        @DisplayName(".PDF (대문자 + 점) → true (case-insensitive 비교)")
        void allowsUppercaseExtensionWithDot() {
            FileUploadProperties props = propsWith(List.of("pdf"), false);

            assertThat(props.isExtensionAllowed("Report.PDF")).isTrue();
            assertThat(props.isExtensionAllowed("report.Pdf")).isTrue();
            assertThat(props.isExtensionAllowed("REPORT.PDF")).isTrue();
        }

        @Test
        @DisplayName(".exe → false (화이트리스트 외)")
        void rejectsNonWhitelistedExtension() {
            FileUploadProperties props = propsWith(
                    List.of("pdf", "jpg", "png", "mp4"),
                    false
            );

            assertThat(props.isExtensionAllowed("malware.exe")).isFalse();
            assertThat(props.isExtensionAllowed("script.sh")).isFalse();
            assertThat(props.isExtensionAllowed("archive.zip")).isFalse();
        }

        @Test
        @DisplayName("이중 확장자는 마지막 확장자로 평가된다 (file.pdf.exe → exe → false)")
        void doubleExtension_evaluatedByLastSegment() {
            FileUploadProperties props = propsWith(List.of("pdf"), false);

            // 마지막 점 이후가 'exe' → 화이트리스트에 없으므로 거부.
            // 확장자 위장 (".pdf.exe") 우회 차단.
            assertThat(props.isExtensionAllowed("contract.pdf.exe")).isFalse();
            // 반대로 마지막이 .pdf 면 통과.
            assertThat(props.isExtensionAllowed("contract.exe.pdf")).isTrue();
        }
    }

    @Nested
    @DisplayName("isExtensionAllowed(): 확장자 없는 파일")
    class NoExtension {

        @Test
        @DisplayName("점 자체가 없는 파일명 → allowFilesWithNoExtension 값을 따른다")
        void filenameWithoutDot_followsFlag() {
            FileUploadProperties strict = propsWith(List.of("pdf"), false);
            FileUploadProperties lenient = propsWith(List.of("pdf"), true);

            assertThat(strict.isExtensionAllowed("README")).isFalse();
            assertThat(lenient.isExtensionAllowed("README")).isTrue();
        }

        @Test
        @DisplayName("점이 맨 끝에 있는 파일명 (e.g. 'file.') → 확장자 없음으로 취급")
        void trailingDot_treatedAsNoExtension() {
            FileUploadProperties strict = propsWith(List.of("pdf"), false);
            FileUploadProperties lenient = propsWith(List.of("pdf"), true);

            assertThat(strict.isExtensionAllowed("file.")).isFalse();
            assertThat(lenient.isExtensionAllowed("file.")).isTrue();
        }

        @Test
        @DisplayName("dotfile (e.g. '.gitignore') 의 확장자는 'gitignore' 로 추출된다")
        void dotfile_extractedAsExtension() {
            FileUploadProperties strict = propsWith(List.of("pdf"), false);
            FileUploadProperties withGitignore = propsWith(List.of("gitignore"), false);

            // 'gitignore' 가 화이트리스트에 없으므로 거부 — allowFilesWithNoExtension
            // 과 무관하게 (점이 있으므로 확장자 분기로 빠짐).
            assertThat(strict.isExtensionAllowed(".gitignore")).isFalse();
            // 명시적으로 'gitignore' 를 허용하면 통과.
            assertThat(withGitignore.isExtensionAllowed(".gitignore")).isTrue();
        }
    }

    @Nested
    @DisplayName("isExtensionAllowed(): 입력 검증")
    class InputValidation {

        @Test
        @DisplayName("null 파일명 → false")
        void nullFilename_returnsFalse() {
            FileUploadProperties props = propsWith(List.of("pdf"), true);

            assertThat(props.isExtensionAllowed(null)).isFalse();
        }

        @Test
        @DisplayName("blank 파일명 → false (공백/빈 문자열)")
        void blankFilename_returnsFalse() {
            FileUploadProperties props = propsWith(List.of("pdf"), true);

            assertThat(props.isExtensionAllowed("")).isFalse();
            assertThat(props.isExtensionAllowed("   ")).isFalse();
        }
    }

    @Nested
    @DisplayName("isExtensionAllowed(): 비활성 화이트리스트")
    class WhitelistDisabled {

        @Test
        @DisplayName("allowed-extensions 가 비어있으면 검증 비활성 → 모든 파일 통과")
        void emptyWhitelist_disablesValidation_allowsEverything() {
            FileUploadProperties props = propsWith(List.of(), false);

            assertThat(props.isExtensionAllowed("anything.exe")).isTrue();
            assertThat(props.isExtensionAllowed("README")).isTrue();
            assertThat(props.isExtensionAllowed("photo.jpg")).isTrue();
        }

        @Test
        @DisplayName("null/blank 만 있는 화이트리스트도 결과적으로 빈 Set → 검증 비활성")
        void whitelistOfOnlyBlanksOrNulls_disablesValidation() {
            FileUploadProperties props = propsWith(
                    Arrays.asList(null, "", "   "),
                    false
            );

            assertThat(props.isExtensionAllowed("anything.exe")).isTrue();
        }
    }
}
