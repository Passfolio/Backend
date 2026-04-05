package com.capstone.passfolio.system.util;

import jakarta.servlet.http.HttpServletRequest;

public class ClientUtils {

    private static final String[] IP_HEADERS = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
    };

    public static String getClientIp(HttpServletRequest request) {
        for (String header : IP_HEADERS) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim(); // X-Forwarded-For는 여러 IP가 올 수 있음 (Client, Proxy1, Proxy2...) -> 첫 번째가 실제 IP
            }
        }
        return request.getRemoteAddr();
    }
}