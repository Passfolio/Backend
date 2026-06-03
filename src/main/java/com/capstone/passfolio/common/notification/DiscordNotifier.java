package com.capstone.passfolio.common.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 운영/디버깅용 Discord 채널 알림(관측 전용, best-effort).
 *
 * <p>webhook URL은 소스에 두지 않고 {@code discord.webhook-url}(env {@code DISCORD_WEBHOOK_URL})로 주입한다.
 * 미설정이면 no-op(에러 없음). 전송은 비동기 fire-and-forget이라 호출 스레드를 막지 않으며,
 * 알림 실패는 본 비즈니스 플로우를 절대 깨지 않는다(예외 swallow + warn).
 */
@Slf4j
@Component
public class DiscordNotifier {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final int DISCORD_CONTENT_LIMIT = 1900; // 실한도 2000, 여유

    private final String webhookUrl;
    private final ObjectMapper objectMapper;

    public DiscordNotifier(@Value("${discord.webhook-url:}") String webhookUrl, ObjectMapper objectMapper) {
        this.webhookUrl = webhookUrl;
        this.objectMapper = objectMapper;
    }

    /** content를 Discord 채널로 전송(미설정/실패 시 조용히 무시 — 관측용). */
    public void send(String content) {
        if (webhookUrl == null || webhookUrl.isBlank() || content == null) {
            return;
        }
        try {
            String text = content.length() > DISCORD_CONTENT_LIMIT
                    ? content.substring(0, DISCORD_CONTENT_LIMIT) + "…" : content;
            String body = objectMapper.writeValueAsString(Map.of("content", text));
            HttpRequest req = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(e -> {
                        log.warn("[Discord] send failed: {}", e.toString());
                        return null;
                    });
        } catch (Exception e) { // 직렬화/URI 등 — 관측 실패가 플로우를 깨지 않게
            log.warn("[Discord] notify error: {}", e.toString());
        }
    }
}
