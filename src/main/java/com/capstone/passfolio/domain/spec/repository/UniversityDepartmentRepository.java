package com.capstone.passfolio.domain.spec.repository;

import com.capstone.passfolio.domain.spec.entity.UniversityDepartment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UniversityDepartmentRepository extends JpaRepository<UniversityDepartment, Long> {

    @Query(
            value = """
                    SELECT ud.department AS department,
                           GREATEST(
                               similarity(ud.department, :keyword),
                               word_similarity(ud.department, :keyword)
                           ) AS similarity
                    FROM university_department ud
                    WHERE ud.university_id = :universityId
                      AND (
                               ud.department ILIKE ('%' || CAST(:keyword AS text) || '%')
                            OR GREATEST(
                                   similarity(ud.department, :keyword),
                                   word_similarity(ud.department, :keyword)
                               ) >= :threshold
                          )
                      AND GREATEST(
                              similarity(ud.department, :keyword),
                              word_similarity(ud.department, :keyword)
                          ) > 0
                    ORDER BY CASE WHEN ud.department ILIKE (CAST(:keyword AS text) || '%') THEN 0 ELSE 1 END,
                             similarity DESC,
                             ud.department ASC
                    LIMIT 1
                    """,
            nativeQuery = true
    )
    Optional<DepartmentSimilarityRow> findBestDepartmentByUniversityIdAndKeyword(
            @Param("universityId") String universityId,
            @Param("keyword") String keyword,
            @Param("threshold") double threshold);

    @Query("""
            SELECT ud
            FROM UniversityDepartment ud
            JOIN FETCH ud.university u
            WHERE u.id = :universityId
              AND ud.department = :department
            ORDER BY ud.id ASC
            """)
    List<UniversityDepartment> findAllByUniversityIdAndDepartment(
            @Param("universityId") String universityId,
            @Param("department") String department);

    interface DepartmentSimilarityRow {
        String getDepartment();
        Double getSimilarity();
    }
}
