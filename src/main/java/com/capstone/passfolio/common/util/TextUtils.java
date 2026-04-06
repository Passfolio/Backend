package com.capstone.passfolio.common.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TextUtils {

    /**
     * 문자열을 지정된 길이로 자르고, 잘린 경우 "..."을 붙입니다.
     * (DB 컬럼 사이즈 초과 방지용)
     *
     * @param text 원본 문자열
     * @param limit 최대 길이 (예: 255)
     * @return 길이 제한에 맞춰 잘린 문자열
     */
    public static String truncate(String text, int limit) {
        if (text == null) {
            return null;
        }
        if (text.length() <= limit) {
            return text;
        }
        // "..." 3글자를 포함하여 limit 길이를 맞춤
        return text.substring(0, limit - 3) + "...";
    }
}