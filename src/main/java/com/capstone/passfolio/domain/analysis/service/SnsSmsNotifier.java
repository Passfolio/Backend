package com.capstone.passfolio.domain.analysis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

/**
 * 배치 완료 SMS(AWS SNS). phone은 BatchPhoneStore에서 transient 조회(요청 시 받은 값, DB 미저장).
 * best-effort — 전송 실패가 배치 완료 처리를 깨지 않는다. aws.sns.enabled=false면 로그만(개발).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SnsSmsNotifier implements SmsNotifier {

    private final SnsClient snsClient;
    private final BatchPhoneStore phoneStore;

    @Value("${aws.sns.enabled:false}")
    private boolean snsEnabled;

    @Override
    public void notifyBatchCompleted(Long userId, String batchId, int total, boolean allSuccess) {
        String phone = phoneStore.read(batchId);
        if (phone == null) {
            log.info("[SMS] no phone for batch, skip. userId={}, batchId={}", userId, batchId);
            return;
        }
        String message = allSuccess
                ? String.format("[Passfolio] 프로젝트 분석 %d건이 모두 완료되었습니다.", total)
                : String.format("[Passfolio] 프로젝트 분석이 종료됐으나 일부 실패했습니다. (총 %d건)", total);

        if (!snsEnabled) {
            log.info("[SMS] (disabled) would send. userId={}, batchId={}, msg={}", userId, batchId, message);
            return;
        }
        try {
            snsClient.publish(PublishRequest.builder().phoneNumber(phone).message(message).build());
            log.info("[SMS] sent. userId={}, batchId={}", userId, batchId);
        } catch (Exception e) { // best-effort
            log.error("[SMS] send failed. userId={}, batchId={}", userId, batchId, e);
        }
    }
}
