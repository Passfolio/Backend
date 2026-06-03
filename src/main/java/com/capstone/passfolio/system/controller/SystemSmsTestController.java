package com.capstone.passfolio.system.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.response.SingleMessageSentResponse;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * system 전용 SMS 발송 점검 API(dev 프로필 한정). 파이프라인(Lambda/SQS/배치)을 우회하고
 * CoolSMS(솔라피) sendOne만 직접 호출해 자격증명·발신번호·실도달을 격리 검증한다.
 * 임의 발송이 가능하므로 운영(prod)에는 빈으로 등록되지 않는다.
 */
@Slf4j
@Profile("dev")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/system/sms")
public class SystemSmsTestController {

    private final DefaultMessageService messageService;

    @Value("${sms.coolsms.from:}")
    private String from;

    @PostMapping("/test")
    public ResponseEntity<SmsTestResult> sendTest(@RequestBody SmsTestRequest request) {
        String to = normalize(request.to());
        String text = (request.text() == null || request.text().isBlank())
                ? "[Passfolio] 솔라피 테스트" : request.text();
        try {
            Message m = new Message();
            m.setFrom(normalize(from));
            m.setTo(to);
            m.setText(text);
            SingleMessageSentResponse res = messageService.sendOne(new SingleMessageSendingRequest(m));
            log.info("[SMS-TEST] sent. to={}, messageId={}, statusCode={}",
                    to, res != null ? res.getMessageId() : null, res != null ? res.getStatusCode() : null);
            return ResponseEntity.ok(new SmsTestResult(true,
                    res != null ? res.getMessageId() : null,
                    res != null ? res.getStatusCode() : null,
                    res != null ? res.getStatusMessage() : null, null));
        } catch (Exception e) {
            // 실패 원인을 그대로 노출(점검 목적).
            log.error("[SMS-TEST] send failed. to={}", to, e);
            return ResponseEntity.ok(new SmsTestResult(false, null, null, null,
                    e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    /** CoolSMS는 국내 로컬 형식(010..., 하이픈 없음)을 받는다. +82/82·하이픈 입력을 허용하기 위한 정규화. */
    private static String normalize(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[\\s-]", "");
        if (digits.startsWith("+82")) {
            return "0" + digits.substring(3);
        }
        if (digits.startsWith("82") && !digits.startsWith("0")) {
            return "0" + digits.substring(2);
        }
        return digits;
    }

    public record SmsTestRequest(String to, String text) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SmsTestResult(boolean sent, String messageId, String statusCode,
                                String statusMessage, String error) { }
}
