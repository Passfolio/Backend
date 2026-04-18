package com.capstone.passfolio.domain.spec.controller;

import com.capstone.passfolio.domain.spec.dto.DevSpecDto;
import com.capstone.passfolio.domain.spec.service.DevSpecService;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/spec")
public class DevSpecController implements DevSpecApiSpecification {

    private final DevSpecService devSpecService;

    @Override
    @GetMapping("/dev-spec")
    public DevSpecDto.UpdateResponse getMyDevSpec(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return devSpecService.getMyDevSpec(userPrincipal);
    }

    @Override
    @GetMapping("/dev-spec/education-history")
    public List<DevSpecDto.EducationHistoryItem> getMyEducationHistory(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return devSpecService.getMyEducationHistory(userPrincipal);
    }

    @Override
    @GetMapping("/dev-spec/career")
    public DevSpecDto.CareerReadResponse getMyCareer(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return devSpecService.getMyCareer(userPrincipal);
    }

    @Override
    @PatchMapping("/dev-spec")
    public DevSpecDto.UpdateResponse updateDevSpec(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody DevSpecDto.UpdateRequest request) {
        return devSpecService.updateDevSpec(request, userPrincipal);
    }
}
