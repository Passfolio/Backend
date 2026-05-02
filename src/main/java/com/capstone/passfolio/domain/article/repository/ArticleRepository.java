package com.capstone.passfolio.domain.article.repository;

import com.capstone.passfolio.domain.article.entity.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * {@link Article} 기본 CRUD 레포지토리.
 *
 * <p>설계 결정:
 * <ul>
 *   <li>{@code deleteAllByIdInBatch} — JpaRepository 내장 메서드를 사용한다.
 *       단 service 계층에서 직접 호출하기 위해 별도 wrapper 를 두지 않는다.
 *       Bulk delete 는 JpaRepository 의 {@code deleteAllByIdInBatch(Iterable)} 로 수행한다.</li>
 *   <li>{@code findAllByCreatedBy} — admin user 삭제 시 해당 작성자가 쓴 모든 Article 을
 *       한 번의 쿼리로 가져온다. {@code fileUrls} ElementCollection 은 LAZY 이므로,
 *       S3 key 추출 시 service 에서 명시적으로 접근하면 추가 쿼리가 발생한다.
 *       부하가 우려되면 {@code JOIN FETCH} 로 전환하면 되지만, admin-user-delete 경로는
 *       hot-path 가 아니므로 현재는 LAZY 유지. (N+1 허용 — bulk-user-delete 는 레어 케이스)</li>
 *   <li>{@code deleteAllArticlesByCreatedBy} — bulk article delete 시 ArticleId 목록이
 *       필요 없을 때 직접 writer ID 로 삭제하는 쿼리. S3 cleanup 을 먼저 수행한 뒤
 *       이 메서드를 호출하는 것이 service 의 책임.</li>
 * </ul>
 */
public interface ArticleRepository extends JpaRepository<Article, Long> {

    /**
     * 특정 작성자({@code createdBy == writerId})의 모든 Article 을 반환한다.
     *
     * <p>admin user 삭제 훅({@code ArticleService.deleteAllByWriter})에서 사용한다.
     * fileUrls ElementCollection 은 LAZY 이므로, 각 Article 의 fileUrls 에 접근하면
     * 추가 SELECT 가 발생한다. admin-user-delete 는 빈도가 낮은 경로이므로 허용한다.
     *
     * @param writerId 작성자 ID ({@link com.capstone.passfolio.common.auditor.UserBaseEntity#getCreatedBy()})
     * @return 해당 작성자의 Article 목록 (없으면 빈 리스트)
     */
    List<Article> findAllByCreatedBy(Long writerId);

    /**
     * 특정 작성자가 작성한 모든 Article 을 DB 에서 bulk delete 한다.
     *
     * <p>S3 cleanup 이 완료된 뒤 호출해야 한다. FK ON DELETE CASCADE 로 인해
     * {@code article_file_urls} 행도 함께 제거된다.
     *
     * @param writerId 작성자 ID
     */
    @Modifying
    @Query("DELETE FROM Article a WHERE a.createdBy = :writerId")
    void deleteAllByCreatedBy(@Param("writerId") Long writerId);
}
