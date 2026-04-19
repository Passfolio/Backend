package com.capstone.passfolio.domain.spec.entity;

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
        name = "dev_spec_education",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_DEV_SPEC_EDUCATION",
                        columnNames = {"dev_spec_id", "university_department_id"}
                )
        }
)
public class DevSpecEducation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dev_spec_id", nullable = false)
    private DevSpec devSpec; // = a User

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_department_id", nullable = false)
    private UniversityDepartment universityDepartment;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public static DevSpecEducation of(DevSpec devSpec, UniversityDepartment universityDepartment, int displayOrder) {
        return DevSpecEducation.builder()
                .devSpec(devSpec)
                .universityDepartment(universityDepartment)
                .displayOrder(displayOrder)
                .build();
    }
}
