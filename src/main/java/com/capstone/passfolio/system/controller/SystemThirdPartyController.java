package com.capstone.passfolio.system.controller;

import com.capstone.passfolio.domain.aws.stepfunction.StepFunctionService;
import com.capstone.passfolio.domain.thirdparty.govdata.LambdaPayloadBuilder;
import com.capstone.passfolio.domain.thirdparty.govdata.UniversityOpenApiTrigger;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/system/third-party")
public class SystemThirdPartyController implements SystemThirdPartyApiSpecification {

    private final UniversityOpenApiTrigger universityOpenApiTrigger;
    private final LambdaPayloadBuilder payloadBuilder;
    private final StepFunctionService stepFunctionService;

    @Override
    @GetMapping("/open-api/probe")
    public ResponseEntity<UniversityOpenApiTrigger.UniversityOpenApiProbeResult> probeUniversityOpenApi() {
        return ResponseEntity.ok(universityOpenApiTrigger.probeUnivMajorOpenApi());
    }

    @Override
    @PostMapping("/open-api/trigger-stepfunction")
    public ResponseEntity<Void> triggerStepFunction() {

        var probe = universityOpenApiTrigger.probeUnivMajorOpenApi();

        String year = probe.yr();
        int totalCount = probe.totalCount();

        List<Map<String, Object>> payloads = payloadBuilder.build(year, totalCount);

        stepFunctionService.execute(payloads);

        return ResponseEntity.accepted().build();
    }
}
