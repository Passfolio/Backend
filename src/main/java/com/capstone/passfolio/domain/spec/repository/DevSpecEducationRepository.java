package com.capstone.passfolio.domain.spec.repository;

import com.capstone.passfolio.domain.spec.entity.DevSpecEducation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DevSpecEducationRepository extends JpaRepository<DevSpecEducation, Long> {

    @Query(
            """
                    SELECT de FROM DevSpecEducation de
                    JOIN FETCH de.universityDepartment ud
                    JOIN FETCH ud.university
                    WHERE de.devSpec.id = :devSpecId
                    ORDER BY de.displayOrder ASC
                    """)
    List<DevSpecEducation> findAllWithUniversityByDevSpecId(@Param("devSpecId") Long devSpecId);
}
