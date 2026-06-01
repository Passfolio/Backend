package com.capstone.passfolio.domain.aws.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * SQS 메시지 전송기. payload를 JSON 직렬화해 지정 큐로 전송한다.
 * (StepFunctionService.execute 패턴 모사 — 실패 시 RuntimeException 전파, 호출 측에서 매핑.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqsMessageSender {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    public void send(String queueUrl, Object payload) {
        try {
            String body = objectMapper.writeValueAsString(payload);
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(body)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("SQS 메시지 전송 실패", e);
        }
    }
}
