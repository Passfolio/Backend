package com.capstone.passfolio.domain.thirdparty.govdata;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class LambdaPayloadBuilder {

    private static final int PAGE_SIZE = 500;
    private static final int LAMBDA_PROCESS_UNIT = 20;

    public List<Map<String, Object>> build(String year, int totalCount) {

        // 전체 페이지 수
        int capablePageSize = (totalCount + PAGE_SIZE - 1) / PAGE_SIZE;

        List<Map<String, Object>> payloads = new ArrayList<>();

        int startPage = 1;

        while (startPage <= capablePageSize) {

            Map<String, Object> payload = new HashMap<>();
            payload.put("year", year);
            payload.put("startPage", startPage);

            // 남은 페이지 계산
            int remaining = capablePageSize - startPage + 1;

            // 마지막 payload 대응
            int currentUnit = Math.min(LAMBDA_PROCESS_UNIT, remaining);

            payload.put("lambdaProcessUnit", currentUnit);

            payloads.add(payload);

            startPage += LAMBDA_PROCESS_UNIT;
        }

        return payloads;
    }
}