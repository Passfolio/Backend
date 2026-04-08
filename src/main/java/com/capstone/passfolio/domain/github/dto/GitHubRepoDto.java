package com.capstone.passfolio.domain.github.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@Schema(description = "GitHub 저장소 정보")
public class GitHubRepoDto {

    @Schema(
        description = "저장소 이름 (owner 제외)",
        example = "backend-service"
    )
    private String name;

    @Schema(
        description = "저장소 설명. GitHub에 설명이 없으면 null입니다.",
        example = "팀 백엔드 서비스",
        nullable = true
    )
    private String description;

    @Schema(
        description = "주 사용 언어. 코드가 없거나 감지 불가 시 null입니다.",
        example = "Java",
        nullable = true
    )
    private String language;
}
