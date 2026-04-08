package com.capstone.passfolio.domain.github.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@Schema(description = "GitHub 프로필 응답")
public class GitHubProfileResponse {

    @Schema(
        description = "GitHub 로그인 ID (username)",
        example = "hooby3dfx"
    )
    private String login;

    @Schema(
        description = "GitHub 표시 이름. GitHub API 실패 시 DB에 저장된 nickname으로 fallback됩니다.",
        example = "Hooby Park",
        nullable = true
    )
    private String name;

    @Schema(
        description = "GitHub 프로필 이미지 URL",
        example = "https://avatars.githubusercontent.com/u/12345678?v=4",
        nullable = true
    )
    private String avatarUrl;
}
