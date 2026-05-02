package com.capstone.passfolio.domain.article.controller;

import com.capstone.passfolio.common.dto.PageDto;
import com.capstone.passfolio.domain.article.dto.ArticleDto;
import com.capstone.passfolio.domain.article.service.ArticleService;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Article 도메인 REST 컨트롤러.
 *
 * <p>Swagger 명세는 {@link ArticleApiSpecification} 인터페이스에 분리되어 있다.
 * 본 클래스는 Spring MVC 어노테이션과 서비스 위임 책임만 진다.
 *
 * <p>인증 정책 요약:
 * <ul>
 *   <li><b>POST / PATCH / DELETE</b> — ADMIN 전용. {@link ArticleService}의 {@code assertAdmin} 가드가
 *       비-ADMIN 요청을 {@code ARTICLE_FORBIDDEN(403)}으로 거부한다.</li>
 *   <li><b>GET /{id}, GET /</b> — 방문자(비로그인) 포함 누구나 접근 가능.
 *       {@code RequestMatcherHolder}가 해당 경로를 {@code permitAll}로 허용하며 (T13 에서 추가),
 *       {@code @AuthenticationPrincipal}은 사용하지 않는다.</li>
 * </ul>
 *
 * @see ArticleApiSpecification
 * @see ArticleService
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/articles")
public class ArticleController implements ArticleApiSpecification {

    private final ArticleService articleService;

    // ======================================================================
    // POST / — 게시글 생성 (ADMIN)
    // ======================================================================

    @Override
    @PostMapping
    public ResponseEntity<ArticleDto.ArticleResponse> create(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody ArticleDto.CreateRequest request) {
        ArticleDto.ArticleResponse response = articleService.create(request, userPrincipal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ======================================================================
    // PATCH /{id} — 게시글 부분 수정 (ADMIN)
    // ======================================================================

    @Override
    @PatchMapping("/{id}")
    public ResponseEntity<ArticleDto.ArticleResponse> update(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody ArticleDto.UpdateRequest request) {
        ArticleDto.ArticleResponse response = articleService.update(id, request, userPrincipal);
        return ResponseEntity.ok(response);
    }

    // ======================================================================
    // DELETE /{id} — 게시글 삭제 (ADMIN)
    // ======================================================================

    @Override
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        articleService.delete(id, userPrincipal);
        return ResponseEntity.noContent().build();
    }

    // ======================================================================
    // GET /{id} — 게시글 단건 조회 (PUBLIC)
    // ======================================================================

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<ArticleDto.ArticleResponse> getById(@PathVariable Long id) {
        ArticleDto.ArticleResponse response = articleService.getById(id);
        return ResponseEntity.ok(response);
    }

    // ======================================================================
    // GET / — 게시글 목록 조회 (PUBLIC, paged)
    // ======================================================================

    @Override
    @GetMapping
    public ResponseEntity<PageDto.PageListResponse<ArticleDto.ArticlePageResponse>> list(
            @Valid @ModelAttribute ArticleDto.ArticlePageRequest request) {
        PageDto.PageListResponse<ArticleDto.ArticlePageResponse> response = articleService.list(request);
        return ResponseEntity.ok(response);
    }
}
