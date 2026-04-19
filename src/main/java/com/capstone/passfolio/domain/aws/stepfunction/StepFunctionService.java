package com.capstone.passfolio.domain.aws.stepfunction;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.StartExecutionRequest;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StepFunctionService {

    private final SfnClient sfnClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.stepfunction.arn}")
    private String stateMachineArn;

    public void execute(List<Map<String, Object>> payloads) {

        try {
            String input = objectMapper.writeValueAsString(
                    Map.of("payloads", payloads)
            );

            StartExecutionRequest request = StartExecutionRequest.builder()
                    .stateMachineArn(stateMachineArn)
                    .input(input)
                    .build();

            sfnClient.startExecution(request);

        } catch (Exception e) {
            throw new RuntimeException("StepFunction 실행 실패", e);
        }
    }
}
