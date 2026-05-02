package com.capstone.passfolio.domain.file.entity.enums;

/**
 * 업로드된 파일의 미디어 카테고리.
 *
 * <p>S3 multipart 업로드 시 MIME prefix(예: {@code image/}, {@code video/}, {@code audio/})
 * 또는 파일 확장자로부터 분류된다. PDF는 별도 카테고리로 둔다.
 *
 * <p>허용 확장자 화이트리스트(pdf + image + video) 외의 입력은
 * {@link com.capstone.passfolio.system.config.file.FileUploadProperties#isExtensionAllowed}
 * 에서 사전 차단되므로 일반 흐름상 {@link #UNKNOWN} 이 영속화될 일은 없다.
 * {@link #UNKNOWN} 은 분류 실패 시의 안전한 fallback 값이다.
 */
public enum MediaType {
    IMAGE,
    VIDEO,
    AUDIO,
    PDF,
    UNKNOWN
}
