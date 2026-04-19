package com.capstone.passfolio.domain.github.controller;

import com.capstone.passfolio.domain.github.dto.GitHubDto;
import com.capstone.passfolio.domain.github.service.GitHubProfileService;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/github")
public class GitHubController implements GitHubApiSpecification {

    private final GitHubProfileService gitHubProfileService;

    @Override
    @GetMapping("/profile")
    public GitHubDto.ProfileResponse getProfile(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return gitHubProfileService.getProfile(userPrincipal.getUserId());
    }

    @Override
    @GetMapping("/repos")
    public GitHubDto.RepoListResponse getRepos(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam String type,
            @RequestParam(required = false) String cursor) {
        return switch (type) {
            case "public" -> gitHubProfileService.getPublicRepos(userPrincipal.getUserId(), cursor);
            case "private" -> gitHubProfileService.getPrivateRepos(userPrincipal.getUserId(), cursor);
            case "organization" -> gitHubProfileService.getOrgRepos(userPrincipal.getUserId(), cursor);
            default -> throw new RestException(
                    ErrorCode.GLOBAL_BAD_REQUEST, "type은 public, private, organization 중 하나여야 합니다.");
        };
    }
}
