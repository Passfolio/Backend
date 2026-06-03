package com.capstone.passfolio.domain.analysis.service;

import com.capstone.passfolio.system.util.PropertiesParserUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 배치 완료 SMS(CoolSMS/솔라피). phone은 BatchPhoneStore에서 transient 조회(요청 시 받은 값, DB 미저장).
 * best-effort — 전송 실패가 배치 완료 처리를 깨지 않는다. sms.coolsms.enabled=false면 로그만(개발).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoolSmsNotifier implements SmsNotifier {

    private final DefaultMessageService messageService;
    private final BatchPhoneStore phoneStore;

    @Value("${sms.coolsms.enabled:false}")
    private boolean smsEnabled;

    @Value("${sms.coolsms.from:}")
    private String from;

    // 결과 보기 링크 base. 콤마 구분 다중 origin일 수 있어 맨 뒤 값을 정식(운영) 도메인으로 사용.
    @Value("${app.front-base-url}")
    private String frontBaseUrlConfig;

    @Override
    public void notifyBatchCompleted(Long userId, String batchId, int total, boolean allSuccess) {
        String phone = phoneStore.read(batchId);
        if (phone == null) {
            log.info("[SMS] no phone for batch, skip. userId={}, batchId={}", userId, batchId);
            return;
        }
        String summary = allSuccess
                ? String.format("[Passfolio] 프로젝트 분석 %d건이 모두 완료되었습니다.", total)
                : String.format("[Passfolio] 프로젝트 분석이 종료됐으나 일부 실패했습니다. (총 %d건)", total);
        // 링크 포함으로 90바이트를 넘기면 솔라피가 자동으로 LMS로 발송한다.
        String message = summary + "\n결과 보기: " + resultLink(batchId);

        if (!smsEnabled) {
            log.info("[SMS] (disabled) would send. userId={}, batchId={}, msg={}", userId, batchId, message);
            return;
        }
        try {
            Message m = new Message();
            m.setFrom(normalize(from));
            m.setTo(normalize(phone));
            m.setText(message);
            messageService.sendOne(new SingleMessageSendingRequest(m));
            log.info("[SMS] sent. userId={}, batchId={}", userId, batchId);
        } catch (Exception e) { // best-effort
            log.error("[SMS] send failed. userId={}, batchId={}", userId, batchId, e);
        }
    }

    @Override
    public void notifyPortfolioCompleted(Long userId, String batchId, boolean success) {
        String phone = phoneStore.read(batchId);
        if (phone == null) {
            log.info("[SMS] no phone for portfolio, skip. userId={}, batchId={}", userId, batchId);
            return;
        }
        String summary = success
                ? "[Passfolio] 포트폴리오 생성이 완료되었습니다."
                : "[Passfolio] 포트폴리오 생성에 실패했습니다.";
        String message = summary + "\n결과 보기: " + resultLink(batchId);

        if (!smsEnabled) {
            log.info("[SMS] (disabled) would send portfolio. userId={}, batchId={}, msg={}", userId, batchId, message);
            return;
        }
        try {
            Message m = new Message();
            m.setFrom(normalize(from));
            m.setTo(normalize(phone));
            m.setText(message);
            messageService.sendOne(new SingleMessageSendingRequest(m));
            log.info("[SMS] portfolio sent. userId={}, batchId={}", userId, batchId);
        } catch (Exception e) { // best-effort
            log.error("[SMS] portfolio send failed. userId={}, batchId={}", userId, batchId, e);
        }
    }

    @Override
    public void notifyPortfolioHandoffFailed(Long userId, String batchId) {
        String phone = phoneStore.read(batchId);
        if (phone == null) {
            log.info("[SMS] no phone for handoff-failed, skip. userId={}, batchId={}", userId, batchId);
            return;
        }
        // 분석은 완료됐으나 포폴 생성 시작에 실패 — 사용자가 결과 페이지에서 재시도하도록 유도.
        String message = "[Passfolio] 프로젝트 분석은 완료됐으나 포트폴리오 생성 시작에 실패했습니다. 결과 페이지에서 재시도해주세요."
                + "\n결과 보기: " + resultLink(batchId);

        if (!smsEnabled) {
            log.info("[SMS] (disabled) would send handoff-failed. userId={}, batchId={}, msg={}", userId, batchId, message);
            return;
        }
        try {
            Message m = new Message();
            m.setFrom(normalize(from));
            m.setTo(normalize(phone));
            m.setText(message);
            messageService.sendOne(new SingleMessageSendingRequest(m));
            log.info("[SMS] handoff-failed sent. userId={}, batchId={}", userId, batchId);
        } catch (Exception e) { // best-effort
            log.error("[SMS] handoff-failed send failed. userId={}, batchId={}", userId, batchId, e);
        }
    }

    /** 사용자에게 보낼 결과 페이지 링크: {front-base-url 맨 뒤 값}/analysis/{batchId}. */
    private String resultLink(String batchId) {
        List<String> bases = PropertiesParserUtils.propertiesParser(frontBaseUrlConfig);
        String base = bases.isEmpty() ? "" : bases.get(bases.size() - 1);
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/analysis/" + batchId;
    }

    /**
     * CoolSMS는 국내 로컬 형식(010..., 하이픈 없음)을 받는다. FE가 E.164(+8210...)로 보낼 수 있어
     * +82/82 국가코드 접두를 0으로 치환하고 하이픈·공백을 제거한다.
     */
    public static String normalize(String raw) {
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
}
