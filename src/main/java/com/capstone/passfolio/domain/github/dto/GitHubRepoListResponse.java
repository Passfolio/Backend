package com.capstone.passfolio.domain.github.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
@Schema(description = "GitHub 저장소 목록 응답")
public class GitHubRepoListResponse {

    @Schema(
        description = "조회한 저장소 타입",
        example = "public",
        allowableValues = {"public", "private", "organization"}
    )
    private String type;

    @Schema(
        description = """
            이번 응답에 포함된 저장소 수.
            public/private: 최대 6개.
            organization: GraphQL 결과에서 org 필터링 후 실제 수 (6개 미만일 수 있음).
            """,
        example = "6"
    )
    private int perPage;

    @Schema(
        description = """
            다음 페이지 커서 (opaque Base64Url 토큰).
            다음 요청의 `cursor` 파라미터로 그대로 전달하세요.
            null이면 마지막 페이지입니다.
            """,
        example = "eyJwIjoyfQ",
        nullable = true
    )
    private String nextCursor;

    @Schema(description = "저장소 목록")
    private List<GitHubRepoDto> repos;
}
