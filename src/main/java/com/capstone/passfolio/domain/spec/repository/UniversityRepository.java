package com.capstone.passfolio.domain.spec.repository;

import com.capstone.passfolio.domain.spec.entity.University;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UniversityRepository extends JpaRepository<University, String> {

    @Query(
            value = """
                    SELECT u.id         AS id,
                           u.name       AS name,
                           u.type       AS type,
                           u.region     AS region,
                           GREATEST(
                               similarity(u.name, :keyword),
                               word_similarity(u.name, :keyword)
                           ) AS similarity
                    FROM university u
                    WHERE (
                               u.name ILIKE ('%' || CAST(:keyword AS text) || '%')
                            OR GREATEST(
                                   similarity(u.name, :keyword),
                                   word_similarity(u.name, :keyword)
                               ) >= :threshold
                          )
                      AND GREATEST(
                              similarity(u.name, :keyword),
                              word_similarity(u.name, :keyword)
                          ) > 0
                    ORDER BY CASE WHEN u.name ILIKE (CAST(:keyword AS text) || '%') THEN 0 ELSE 1 END,
                             similarity DESC,
                             u.name ASC
                    """,
            nativeQuery = true
    )
    List<UniversitySimilarityRow> findTopMatchesByName(
            @Param("keyword") String keyword,
            @Param("threshold") double threshold);

    interface UniversitySimilarityRow {
        String getId();
        String getName();
        String getType();
        String getRegion();
        Double getSimilarity();
    }
}
