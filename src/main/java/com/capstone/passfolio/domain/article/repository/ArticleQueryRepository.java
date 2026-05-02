package com.capstone.passfolio.domain.article.repository;

import com.capstone.passfolio.domain.article.dto.ArticleDto;
import com.capstone.passfolio.domain.article.dto.QArticleDto_ArticlePageResponse;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.capstone.passfolio.domain.article.entity.QArticle.article;

/**
 * Article 목록 조회 전용 QueryDSL 레포지토리.
 *
 * <p>설계 결정 (요약 — 상세는 {@code 0001-harness-design.md} 참고, 그리고
 * {@code Backend/.claude/skills/offset-pagination/SKILL.md}):
 *
 * <ul>
 *   <li><b>2-step query 패턴 (offset-pagination 스킬 핵심)</b>:
 *     <ol>
 *       <li>STEP 1 — covering index {@code idx_article_created (created_at DESC, id DESC)} 만 타고
 *           {@code id} 리스트만 조회. {@code offset} / {@code limit} 은 여기에만 적용. join, 본문 컬럼
 *           접근 일체 금지 — 인덱스 페이지만 읽어 deep page 에서도 빠르게 응답.</li>
 *       <li>STEP 2 — STEP 1에서 얻은 ID 들에 대해 {@code WHERE id IN (...)} 으로 본 데이터를 조회하고
 *           {@code @QueryProjection} 으로 {@link ArticleDto.ArticlePageResponse} 를 직접 만든다. {@code IN}
 *           절은 입력 순서를 보장하지 않으므로 STEP 1 ID 순서대로 재정렬한다.</li>
 *       <li>COUNT — {@code articles} 테이블만 카운트. {@link PageableExecutionUtils#getPage} 가 마지막
 *           페이지가 아니거나 결과가 page size 미달일 때 자체적으로 count 쿼리를 스킵한다 (lazy).</li>
 *     </ol>
 *   </li>
 *   <li><b>Stats 분리 없음</b> — 단일 정렬축({@code createdAt})뿐이라 본 엔티티의 covering index 1개로
 *       충분하다. 다중 정렬축 + 빈번한 메트릭 update가 생기면 그때 Stats 분리.</li>
 *   <li><b>{@code id DESC} tiebreaker 강제</b> — pageable 의 sort 에 {@code id} 가 없으면 마지막에 자동
 *       추가 ({@link #getOrderSpecifiers}). DTO ({@link ArticleDto.ArticlePageRequest#toPageable()}) 가
 *       이미 {@code id DESC} 를 붙여 전달하지만, 다른 호출자가 있을 수 있어 헬퍼 차원에서 한 번 더 보장.</li>
 *   <li><b>화이트리스트 (스킬 규칙 4)</b> — {@code switch} 문에서 {@code "createdAt"} 만 매핑하고 나머지는
 *       {@code null} 로 drop. 임의 컬럼명으로 정렬 시도가 와도 인덱스 미사용 풀스캔/filesort 로 빠지지 않게
 *       방어한다. DTO 의 {@code @Pattern} 검증과 이중 방어선.</li>
 *   <li><b>STEP 2에서 join 안 함</b> — {@code thumbnail} / {@code title} / {@code createdAt} / {@code createdBy}
 *       모두 {@code articles} 테이블 자체 컬럼 (썸네일은 비정규화). 작성자 닉네임은 {@link ArticleDto.ArticlePageResponse}
 *       에서 의도적으로 제외 — 목록 N+1 회피.</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class ArticleQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 게시글 목록을 페이지네이션으로 조회한다.
     *
     * <p>2-step query 로 구현 — STEP 1에서 covering index만 타고 ID를 뽑고, STEP 2에서 본 데이터를
     * 가져온다. count 는 {@link PageableExecutionUtils} 가 lazy 로 호출한다.
     *
     * @param pageable {@link ArticleDto.ArticlePageRequest#toPageable()} 으로 만들어진 Pageable.
     *                 {@code id DESC} tiebreaker 가 보조 정렬축으로 이미 붙어있어야 한다.
     * @return 페이지 콘텐츠 ({@link ArticleDto.ArticlePageResponse}) 와 메타데이터.
     */
    public Page<ArticleDto.ArticlePageResponse> searchArticlePage(Pageable pageable) {

        // ============ STEP 1: ID 만 조회 (covering index 전용) ============
        // ⚠️ 여기서는 join 절대 금지. select/where/orderBy 는 모두 idx_article_created 가
        //    커버하는 컬럼 (id, created_at) 만 사용해야 한다.
        List<Long> ids = queryFactory
                .select(article.id)
                .from(article)
                .orderBy(getOrderSpecifiers(pageable))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // ============ STEP 2: 본 데이터 조회 (WHERE id IN (...)) ============
        List<ArticleDto.ArticlePageResponse> sortedContent = new ArrayList<>();

        if (!ids.isEmpty()) {
            List<ArticleDto.ArticlePageResponse> content = queryFactory
                    .select(new QArticleDto_ArticlePageResponse(
                            article.id,
                            article.title,
                            article.thumbnail,
                            article.createdAt,
                            article.createdBy
                    ))
                    .from(article)
                    .where(article.id.in(ids))
                    .fetch();

            // ⚠️ IN 절은 입력 순서를 보장하지 않는다. STEP 1 의 ids 순서대로 재정렬.
            Map<Long, ArticleDto.ArticlePageResponse> contentMap = content.stream()
                    .collect(Collectors.toMap(ArticleDto.ArticlePageResponse::getId, Function.identity()));
            sortedContent = ids.stream().map(contentMap::get).toList();
        }

        // ============ COUNT: articles 테이블만 (lazy execution) ============
        // 마지막 페이지가 아니거나 size 미달이면 PageableExecutionUtils 가 count 쿼리를 스킵한다.
        JPAQuery<Long> countQuery = queryFactory
                .select(article.count())
                .from(article);

        return PageableExecutionUtils.getPage(sortedContent, pageable, countQuery::fetchOne);
    }

    // ============ 정렬 — 화이트리스트 + ID tiebreaker ============

    /**
     * pageable 의 sort 를 QueryDSL {@link OrderSpecifier} 배열로 변환한다.
     *
     * <p>규칙 (offset-pagination 스킬):
     * <ul>
     *   <li>화이트리스트에 없는 정렬 컬럼은 {@code null} 반환 → 인덱스 미사용 정렬 차단.</li>
     *   <li>유효한 정렬이 하나도 없으면 {@code createdAt DESC} 기본값 적용.</li>
     *   <li>마지막에 {@code id} 가 없으면 자동으로 {@code id DESC} 추가 (tiebreaker, 페이지 안정성).</li>
     * </ul>
     */
    private OrderSpecifier<?>[] getOrderSpecifiers(Pageable pageable) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();

        for (Sort.Order order : pageable.getSort()) {
            Order direction = order.getDirection().isAscending() ? Order.ASC : Order.DESC;
            OrderSpecifier<?> spec = switch (order.getProperty()) {
                case "createdAt" -> new OrderSpecifier<>(direction, article.createdAt);
                case "id"        -> new OrderSpecifier<>(direction, article.id);
                default          -> null; // ⚠️ 화이트리스트에 없으면 의도적으로 drop
            };
            if (spec != null) {
                orders.add(spec);
            }
        }

        if (orders.isEmpty()) {
            // 정렬이 비어있거나 모두 invalid 면 기본값 적용
            orders.add(new OrderSpecifier<>(Order.DESC, article.createdAt));
        }

        // ID tiebreaker 자동 추가
        boolean hasIdSort = orders.stream().anyMatch(o -> o.getTarget().equals(article.id));
        if (!hasIdSort) {
            orders.add(new OrderSpecifier<>(Order.DESC, article.id));
        }

        return orders.toArray(new OrderSpecifier[0]);
    }
}
