package com.capstone.passfolio.domain.article.controller;

import com.capstone.passfolio.common.dto.PageDto;
import com.capstone.passfolio.domain.article.dto.ArticleDto;
import com.capstone.passfolio.system.exception.dto.ErrorResponse;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Article 도메인 REST API Swagger 명세 인터페이스.
 *
 * <p>컨트롤러({@link ArticleController})는 이 인터페이스를 구현하여 Swagger 어노테이션을
 * 구현체 메서드에서 분리한다 — 프로젝트 표준 패턴 ({@code DevSpecApiSpecification} 등과 동일).
 *
 * <p>인증 정책:
 * <ul>
 *   <li><b>ADMIN-only writes</b> — POST / PATCH / DELETE 엔드포인트는 {@code bearerAuth}
 *       SecurityRequirement 가 선언되어 있으며, 서비스 레이어가 role을 추가 검증한다.</li>
 *   <li><b>PUBLIC reads</b> — GET 엔드포인트는 SecurityRequirement 없이 방문자도 호출 가능하다.
 *       {@code RequestMatcherHolder}가 해당 경로를 {@code permitAll}로 허용한다.</li>
 * </ul>
 *
 * @see ArticleController
 * @see com.capstone.passfolio.domain.article.service.ArticleService
 */
@Tag(
        name = "Article",
        description = "아티클(게시글) CRUD API — ADMIN 쓰기, 방문자/USER 읽기")
public interface ArticleApiSpecification {

    // ======================================================================
    // POST / — 게시글 생성 (ADMIN)
    // ======================================================================

    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "게시글 생성",
            description = """
                    새 아티클을 생성합니다. **ADMIN 역할**이 필요합니다.

                    - `fileUrls` 는 선택이며 CDN URL 목록입니다. 첫 번째 이미지 URL이 자동으로 `thumbnail`이 됩니다.
                    - 이미지 판별 기준: 확장자가 `.jpg`, `.jpeg`, `.png`, `.gif`, `.webp`, `.bmp`, `.svg` 중 하나.
                    - `fileUrls` 를 생략하거나 빈 배열을 보내면 `thumbnail`은 `null`이 됩니다.
                    """)
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "게시글 생성 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ArticleDto.ArticleResponse.class),
                            examples = @ExampleObject(
                                    name = "생성 성공",
                                    value = """
                                            {
                                              "id": 1,
                                              "title": "공지: 5월 정기 점검 안내",
                                              "contents": "안녕하세요, ...",
                                              "fileUrls": ["https://cdn.example.com/articles/1.png"],
                                              "thumbnail": "https://cdn.example.com/articles/1.png",
                                              "writerId": 42,
                                              "writerNickname": "admin",
                                              "createdAt": "2026-05-02T21:00:00",
                                              "lastModifiedAt": "2026-05-02T21:00:00"
                                            }
                                            """))),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력 유효성 검사 실패 (`GLOBAL_INVALID_PARAMETER`)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "title 빈 값",
                                            value = """
                                                    {
                                                      "status": "BAD_REQUEST",
                                                      "error": "GLOBAL_INVALID_PARAMETER",
                                                      "message": "title 은 비어있을 수 없습니다."
                                                    }
                                                    """),
                                    @ExampleObject(
                                            name = "fileUrls 요소 URL 형식 오류",
                                            value = """
                                                    {
                                                      "status": "BAD_REQUEST",
                                                      "error": "GLOBAL_INVALID_PARAMETER",
                                                      "message": "fileUrls 요소는 유효한 URL 이어야 합니다."
                                                    }
                                                    """)
                            })),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "JWT_EXPIRED",
                                    value = """
                                            {
                                              "status": "UNAUTHORIZED",
                                              "error": "JWT_EXPIRED",
                                              "message": "만료된 토큰입니다."
                                            }
                                            """))),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 — ADMIN 역할이 아닌 사용자 (`ARTICLE_FORBIDDEN`)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "ARTICLE_FORBIDDEN",
                                    value = """
                                            {
                                              "status": "FORBIDDEN",
                                              "error": "ARTICLE_FORBIDDEN",
                                              "message": "아티클 쓰기는 ADMIN 만 가능합니다."
                                            }
                                            """)))
    })
    ResponseEntity<ArticleDto.ArticleResponse> create(
            @Parameter(hidden = true) UserPrincipal userPrincipal,
            @RequestBody(
                    description = "게시글 생성 요청 본문",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ArticleDto.CreateRequest.class),
                            examples = @ExampleObject(
                                    name = "이미지 포함 게시글",
                                    value = """
                                            {
                                              "title": "공지: 5월 정기 점검 안내",
                                              "contents": "안녕하세요, 서비스 점검이 예정되어 있습니다.",
                                              "fileUrls": ["https://cdn.example.com/articles/1.png"]
                                            }
                                            """)))
            @Valid ArticleDto.CreateRequest request);

    // ======================================================================
    // PATCH /{id} — 게시글 부분 수정 (ADMIN)
    // ======================================================================

    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "게시글 부분 수정 (PATCH)",
            description = """
                    기존 아티클을 부분 업데이트합니다. **ADMIN 역할**이 필요합니다.

                    - 모든 필드는 선택(`null` = 변경 없음).
                    - 빈 배열 `[]`을 `fileUrls`에 보내면 파일 목록과 썸네일이 모두 비워집니다.
                    - 모든 필드가 `null`인 요청은 `400`으로 거부됩니다.
                    - 기존 `fileUrls`에서 제거된 URL의 S3 객체는 자동으로 삭제됩니다.
                    """)
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "게시글 수정 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ArticleDto.ArticleResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "유효성 검사 실패 또는 변경 필드 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "모든 필드 null",
                                    value = """
                                            {
                                              "status": "BAD_REQUEST",
                                              "error": "GLOBAL_INVALID_PARAMETER",
                                              "message": "변경할 필드가 하나 이상 있어야 합니다."
                                            }
                                            """))),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (`ARTICLE_FORBIDDEN`)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글 없음 (`ARTICLE_NOT_FOUND`)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "ARTICLE_NOT_FOUND",
                                    value = """
                                            {
                                              "status": "NOT_FOUND",
                                              "error": "ARTICLE_NOT_FOUND",
                                              "message": "존재하지 않는 아티클입니다."
                                            }
                                            """)))
    })
    ResponseEntity<ArticleDto.ArticleResponse> update(
            @Parameter(description = "수정할 게시글 ID", required = true, example = "1")
            Long id,
            @Parameter(hidden = true) UserPrincipal userPrincipal,
            @RequestBody(
                    description = "게시글 부분 업데이트 요청. 변경할 필드만 포함하세요. null = 변경 없음.",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ArticleDto.UpdateRequest.class),
                            examples = @ExampleObject(
                                    name = "제목만 수정",
                                    value = """
                                            {
                                              "title": "공지: 5월 정기 점검 안내 (수정)"
                                            }
                                            """)))
            @Valid ArticleDto.UpdateRequest request);

    // ======================================================================
    // DELETE /{id} — 게시글 삭제 (ADMIN)
    // ======================================================================

    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "게시글 삭제",
            description = """
                    아티클을 삭제합니다. **ADMIN 역할**이 필요합니다.

                    - 연관된 `fileUrls`의 S3 객체를 일괄 삭제한 뒤 DB 행을 제거합니다.
                    - S3 삭제 실패 시 로그 기록 후 DB 삭제를 계속 진행합니다(fail-soft).
                    """)
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "게시글 삭제 성공 (No Content)"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (`ARTICLE_FORBIDDEN`)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글 없음 (`ARTICLE_NOT_FOUND`)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "ARTICLE_NOT_FOUND",
                                    value = """
                                            {
                                              "status": "NOT_FOUND",
                                              "error": "ARTICLE_NOT_FOUND",
                                              "message": "존재하지 않는 아티클입니다."
                                            }
                                            """)))
    })
    ResponseEntity<Void> delete(
            @Parameter(description = "삭제할 게시글 ID", required = true, example = "1")
            Long id,
            @Parameter(hidden = true) UserPrincipal userPrincipal);

    // ======================================================================
    // GET /{id} — 게시글 단건 조회 (PUBLIC)
    // ======================================================================

    @Operation(
            summary = "게시글 단건 조회",
            description = """
                    특정 아티클을 조회합니다. **인증 불필요 (방문자 접근 가능).**

                    - `writerNickname`은 작성자가 삭제된 경우 `null`일 수 있습니다.
                    """)
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "게시글 조회 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ArticleDto.ArticleResponse.class),
                            examples = @ExampleObject(
                                    name = "조회 성공",
                                    value = """
                                            {
                                              "id": 1,
                                              "title": "공지: 5월 정기 점검 안내",
                                              "contents": "안녕하세요, ...",
                                              "fileUrls": ["https://cdn.example.com/articles/1.png"],
                                              "thumbnail": "https://cdn.example.com/articles/1.png",
                                              "writerId": 42,
                                              "writerNickname": "admin",
                                              "createdAt": "2026-05-02T21:00:00",
                                              "lastModifiedAt": "2026-05-02T21:00:00"
                                            }
                                            """))),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글 없음 (`ARTICLE_NOT_FOUND`)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "ARTICLE_NOT_FOUND",
                                    value = """
                                            {
                                              "status": "NOT_FOUND",
                                              "error": "ARTICLE_NOT_FOUND",
                                              "message": "존재하지 않는 아티클입니다."
                                            }
                                            """)))
    })
    ResponseEntity<ArticleDto.ArticleResponse> getById(
            @Parameter(description = "조회할 게시글 ID", required = true, example = "1")
            Long id);

    // ======================================================================
    // GET / — 게시글 목록 조회 (PUBLIC, paged)
    // ======================================================================

    @Operation(
            summary = "게시글 목록 조회 (페이지네이션)",
            description = """
                    아티클 목록을 오프셋 페이지네이션으로 반환합니다. **인증 불필요 (방문자 접근 가능).**

                    ## 쿼리 파라미터

                    | 파라미터 | 타입 | 기본값 | 제약 | 설명 |
                    |----------|------|--------|------|------|
                    | `page` | int | 0 | ≥ 0 | 페이지 번호 (0-based) |
                    | `size` | int | 9 | 1 ~ 100 | 한 페이지 항목 수 |
                    | `sort` | string | `createdAt` | `createdAt` 만 허용 | 정렬 기준 컬럼 |
                    | `direction` | string | `DESC` | `ASC` 또는 `DESC` | 정렬 방향 |

                    ## 성능 특성
                    - STEP 1: covering index (`created_at DESC, id DESC`) 스캔 → id 목록
                    - STEP 2: IN-query 본문 fetch → DTO projection
                    - COUNT: lazy (Spring Data `PageableExecutionUtils`)
                    - 1M 행, page=99,990 cold-cache 기준 < 4s (SLA)

                    ## 오류
                    - `page` 값이 전체 페이지 수를 초과하면 `404 PAGE_NOT_FOUND`.
                    - `sort` 화이트리스트 외 값이면 `400 GLOBAL_INVALID_PARAMETER`.
                    """)
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "게시글 목록 조회 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ArticlePageListResponseDoc.class),
                            examples = @ExampleObject(
                                    name = "첫 페이지",
                                    value = """
                                            {
                                              "title": "아티클 목록",
                                              "page": {
                                                "page": 0,
                                                "size": 9,
                                                "totalElements": 20,
                                                "totalPages": 3,
                                                "hasNext": true,
                                                "hasPrev": false
                                              },
                                              "content": [
                                                {
                                                  "id": 20,
                                                  "title": "최신 공지",
                                                  "thumbnail": "https://cdn.example.com/articles/20.png",
                                                  "createdAt": "2026-05-02T20:00:00",
                                                  "writerId": 42
                                                }
                                              ]
                                            }
                                            """))),
            @ApiResponse(
                    responseCode = "400",
                    description = "쿼리 파라미터 유효성 검사 실패 (`GLOBAL_INVALID_PARAMETER`)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "page 음수",
                                            value = """
                                                    {
                                                      "status": "BAD_REQUEST",
                                                      "error": "GLOBAL_INVALID_PARAMETER",
                                                      "message": "page 는 0 이상이어야 합니다."
                                                    }
                                                    """),
                                    @ExampleObject(
                                            name = "sort 화이트리스트 위반",
                                            value = """
                                                    {
                                                      "status": "BAD_REQUEST",
                                                      "error": "GLOBAL_INVALID_PARAMETER",
                                                      "message": "sort 는 'createdAt' 만 허용됩니다."
                                                    }
                                                    """)
                            })),
            @ApiResponse(
                    responseCode = "404",
                    description = "페이지 번호가 전체 페이지 수를 초과 (`PAGE_NOT_FOUND`)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "PAGE_NOT_FOUND",
                                    value = """
                                            {
                                              "status": "NOT_FOUND",
                                              "error": "PAGE_NOT_FOUND",
                                              "message": "요청한 페이지가 범위를 벗어났습니다."
                                            }
                                            """)))
    })
    ResponseEntity<PageDto.PageListResponse<ArticleDto.ArticlePageResponse>> list(
            @Parameter(hidden = true)
            @Valid ArticleDto.ArticlePageRequest request);

    /**
     * Swagger Schema 가 제네릭 타입({@code PageListResponse<ArticlePageResponse>})을
     * 직접 인식하지 못해 렌더링이 깨지는 문제를 방지하기 위한 문서 전용 내부 클래스.
     *
     * <p>실제 응답 타입은 {@link PageDto.PageListResponse}{@code <}{@link ArticleDto.ArticlePageResponse}{@code >}
     * 이며 이 클래스는 Swagger UI 표시 목적으로만 사용한다.
     *
     * @see PageDto.PageListResponse
     * @see ArticleDto.ArticlePageResponse
     */
    @Schema(description = "게시글 목록 응답 (PageListResponse<ArticlePageResponse> 전개)")
    class ArticlePageListResponseDoc {

        @Schema(description = "페이지 제목", example = "아티클 목록")
        public String title;

        @Schema(description = "페이지 메타데이터")
        public PageDto.PageInfo page;

        @Schema(description = "현재 페이지의 게시글 목록")
        public java.util.List<ArticleDto.ArticlePageResponse> content;
    }
}
