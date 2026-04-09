package com.capstone.passfolio.domain.github.util;

import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * GitHub 저장소 목록 API의 cursor 기반 페이지네이션 유틸리티.
 * <p>
 * - REST 페이지네이션 (public/private): {@code {"p":N}} 형식
 * - org 페이지네이션: {@code {"c":"<graphqlEndCursor>","o":<offset>}} 형식
 *   - c: 다음 GraphQL 청크의 after 커서 (빈 문자열이면 첫 GraphQL 페이지)
 *   - o: 해당 GraphQL 청크 내 org repo 목록에서 건너뛸 수 (offset)
 * <p>
 * 클라이언트에게 두 형식 모두 불투명(opaque)한 Base64Url 토큰으로 노출된다.
 */
public final class GitHubCursorUtils {

    private GitHubCursorUtils() {}

    /**
     * org repo 페이지네이션 커서의 내부 구조.
     *
     * @param after  GitHub GraphQL {@code after} 파라미터. null이면 첫 GraphQL 페이지.
     * @param offset 해당 GraphQL 청크에서 이미 전달한 org repo 수 (건너뛸 수).
     */
    public record OrgCursor(String after, int offset) {}

    // ── REST 페이지네이션 (public / private) ─────────────────────────────────

    /**
     * cursor를 GitHub REST API page 번호로 디코딩한다.
     * cursor가 null이거나 비어있으면 첫 페이지(1)를 반환한다.
     *
     * @param cursor Base64Url 인코딩된 커서 문자열 (nullable)
     * @return GitHub API page 번호 (1-indexed)
     * @throws RestException cursor 형식이 유효하지 않은 경우
     */
    public static int decodePage(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 1;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            String json = new String(decoded, StandardCharsets.UTF_8);
            // {"p":N} 형식에서 N 추출
            int colonIdx = json.indexOf(':');
            int braceIdx = json.lastIndexOf('}');
            if (colonIdx < 0 || braceIdx < 0) {
                throw new RestException(ErrorCode.GLOBAL_BAD_REQUEST, "유효하지 않은 커서입니다.");
            }
            int page = Integer.parseInt(json.substring(colonIdx + 1, braceIdx).trim());
            if (page < 1) {
                throw new RestException(ErrorCode.GLOBAL_BAD_REQUEST, "유효하지 않은 커서입니다.");
            }
            return page;
        } catch (IllegalArgumentException e) {
            throw new RestException(ErrorCode.GLOBAL_BAD_REQUEST, "유효하지 않은 커서입니다.");
        }
    }

    /**
     * 다음 REST 페이지 번호를 cursor 문자열로 인코딩한다.
     *
     * @param nextPage 다음 GitHub REST API page 번호
     * @param hasMore  다음 페이지 존재 여부 (GitHub Link 헤더 기준)
     * @return Base64Url 인코딩된 cursor 문자열, 다음 페이지가 없으면 null
     */
    public static String encodeCursor(int nextPage, boolean hasMore) {
        if (!hasMore) {
            return null;
        }
        String json = "{\"p\":" + nextPage + "}";
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    // ── org GraphQL 페이지네이션 ───────────────────────────────────────────

    /**
     * org cursor를 디코딩하여 {@link OrgCursor}를 반환한다.
     * cursor가 null이거나 비어있으면 첫 페이지 {@code OrgCursor(null, 0)}을 반환한다.
     *
     * @param cursor Base64Url 인코딩된 커서 문자열 (nullable)
     * @return OrgCursor(after, offset)
     * @throws RestException cursor 형식이 유효하지 않은 경우
     */
    public static OrgCursor decodeOrgCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new OrgCursor(null, 0);
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            String json = new String(decoded, StandardCharsets.UTF_8);
            // {"c":"<endCursor>","o":N} 형식 파싱
            String c = extractJsonString(json, "c");
            int o = extractJsonInt(json, "o");
            return new OrgCursor(c.isEmpty() ? null : c, o);
        } catch (IllegalArgumentException e) {
            throw new RestException(ErrorCode.GLOBAL_BAD_REQUEST, "유효하지 않은 커서입니다.");
        }
    }

    /**
     * org cursor를 인코딩한다.
     *
     * @param graphqlCursor 다음 GraphQL 청크의 after 커서 (null이면 첫 GraphQL 페이지)
     * @param nextOffset    다음 요청에서 건너뛸 org repo 수
     * @param hasMore       다음 페이지 존재 여부
     * @return Base64Url 인코딩된 cursor 문자열, 다음 페이지가 없으면 null
     */
    public static String encodeOrgCursor(String graphqlCursor, int nextOffset, boolean hasMore) {
        if (!hasMore) {
            return null;
        }
        String c = graphqlCursor != null ? graphqlCursor : "";
        String escaped = c.replace("\\", "\\\\").replace("\"", "\\\"");
        String json = "{\"c\":\"" + escaped + "\",\"o\":" + nextOffset + "}";
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    // ── JSON 파싱 헬퍼 (내부 전용) ────────────────────────────────────────

    /**
     * JSON 문자열에서 특정 키의 string 값을 추출한다.
     * 이스케이프 시퀀스(\\, \")를 처리한다.
     */
    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return "";
        start += search.length();
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (escaped) {
                sb.append(ch);
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '"') {
                break;
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /**
     * JSON 문자열에서 특정 키의 int 값을 추출한다.
     */
    private static int extractJsonInt(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start < 0) return 0;
        start += search.length();
        while (start < json.length() && json.charAt(start) == ' ') start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        if (start == end) return 0;
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            throw new RestException(ErrorCode.GLOBAL_BAD_REQUEST);
        }
    }
}
