package com.capstone.passfolio.system.config.file;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 파일 업로드 정책 (확장자 화이트리스트, 최대 크기 등)을 application.yml에서 주입.
 *
 * 예:
 * file:
 *   upload:
 *     allowed-extensions: ${FILE_UPLOAD_ALLOWED_EXTENSIONS:pdf,jpg,jpeg,png,gif,webp,bmp,svg,mp4,mov,webm,avi,wmv,flv,mkv}
 *     max-file-size-bytes: ${FILE_UPLOAD_MAX_FILE_SIZE_BYTES:104857600}     # 100MB
 *     presigned-url-ttl-minutes: ${FILE_UPLOAD_PRESIGNED_TTL_MIN:10}
 *
 * Spring Boot가 환경변수의 콤마 구분 문자열을 List<String>으로 자동 바인딩.
 *
 * 보안 노트:
 *  - 확장자가 없는 파일은 거부 (allowFilesWithNoExtension=false 기본).
 *  - 비교는 소문자. 대소문자 차이로 우회 불가.
 *  - 이 화이트리스트는 1차 가드일 뿐, 진짜 정체성 검증은 업로드 후 magic number 검사가 한다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "file.upload")
public class FileUploadProperties {

    /**
     * 허용된 확장자 리스트 (점 없이, 소문자 권장). 비어있으면 검증 비활성.
     * 백로그 제약: PDF + 이미지 + 비디오 화이트리스트.
     */
    private List<String> allowedExtensions = List.of(
            "pdf",
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg",
            "mp4", "mov", "webm", "avi", "wmv", "flv", "mkv"
    );

    /** 비즈니스 허용 최대 파일 크기 (bytes). 백로그 제약: 100MB (104857600). */
    private long maxFileSizeBytes = 104857600L;

    /** Presigned URL 만료시간 (분). 기본 10분. */
    private int presignedUrlTtlMinutes = 10;

    /** 확장자가 없는 파일을 허용할지. 기본 false. */
    private boolean allowFilesWithNoExtension = false;

    private Set<String> normalizedAllowedExtensions = Set.of();

    @PostConstruct
    void normalize() {
        // 들어온 값에서 점, 공백, 대문자 정리. 한 번만 만들어두고 비교에 재사용.
        this.normalizedAllowedExtensions = allowedExtensions.stream()
                .filter(e -> e != null && !e.isBlank())
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .map(e -> e.startsWith(".") ? e.substring(1) : e)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 파일명의 확장자가 화이트리스트에 있는지 검증.
     * @return true면 통과, false면 거부.
     */
    public boolean isExtensionAllowed(String filename) {
        if (normalizedAllowedExtensions.isEmpty()) {
            // 화이트리스트 자체가 비어있으면 검증 비활성. 의도적 운영 결정으로 본다.
            return true;
        }
        if (filename == null || filename.isBlank()) return false;

        int dotIdx = filename.lastIndexOf('.');
        if (dotIdx < 0 || dotIdx == filename.length() - 1) {
            return allowFilesWithNoExtension;
        }
        String ext = filename.substring(dotIdx + 1).toLowerCase(Locale.ROOT);
        return normalizedAllowedExtensions.contains(ext);
    }
}
