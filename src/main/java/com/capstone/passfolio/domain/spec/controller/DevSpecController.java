package com.capstone.passfolio.domain.spec.controller;

import com.capstone.passfolio.domain.spec.dto.DevSpecDto;
import com.capstone.passfolio.domain.spec.service.DevSpecService;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/spec")
public class DevSpecController implements DevSpecApiSpecification {

    private final DevSpecService devSpecService;

    @Override
    @PatchMapping("/dev-spec")
    public DevSpecDto.UpdateResponse updateDevSpec(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody DevSpecDto.UpdateRequest request) {
        return devSpecService.updateDevSpec(request, userPrincipal);
    }
}
