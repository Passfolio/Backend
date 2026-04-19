package com.capstone.passfolio.domain.spec.repository;

import com.capstone.passfolio.domain.spec.entity.DevSpecCareer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DevSpecCareerRepository extends JpaRepository<DevSpecCareer, Long> {

    @Query(
            """
                    SELECT dc FROM DevSpecCareer dc
                    JOIN FETCH dc.career
                    WHERE dc.devSpec.id = :devSpecId
                    ORDER BY dc.id ASC
                    """)
    List<DevSpecCareer> findAllWithCareerByDevSpecId(@Param("devSpecId") Long devSpecId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM DevSpecCareer dc WHERE dc.devSpec.id = :devSpecId")
    void deleteAllByDevSpecId(@Param("devSpecId") Long devSpecId);
}
