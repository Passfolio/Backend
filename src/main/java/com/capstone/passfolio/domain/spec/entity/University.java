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
                        columnNames = { "name", "type", "region" }
                )
        }
)
public class University {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id; // Name Base UUId

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type; // Ex. 명지대, 명지전문대, ...

    @Column(nullable = false)
    private String region;
}
