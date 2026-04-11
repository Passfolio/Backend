package com.capstone.passfolio.domain.spec.entity;

import com.capstone.passfolio.common.util.UuidGenerator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "university",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_UNIVERSITY_CAMPUS",
                        columnNames = {
                                "name",
                                "education_type",
                                "campus_type",
                                "region"
                        }
                )
        }
)
public class University {

    /**
     * 학교 인스턴스(교명·학제·캠퍼스·지역) 기반 결정적 UUID. 학과와 무관.
     */
    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String educationType;

    @Column(nullable = false)
    private String campusType;

    @Column(nullable = false)
    private String region;

    @Column(nullable = false)
    private String pageUrl;


    // ----- Helper Methods ----- //

    /* Name based UUID PK Generator
     * convert uk -> uuid -? to make consistent mapping table
     * - Writer : Hooby
     *  */
    private static final String CANONICAL_UNIT_SEPARATOR = "\u001e";
    public static String deterministicId(
            String name,
            String educationType,
            String campusType,
            String region) {

        String canonical = String.join(
                CANONICAL_UNIT_SEPARATOR,
                name,
                educationType,
                campusType,
                region);

        return UuidGenerator.generate(canonical).toString();
    }
}
