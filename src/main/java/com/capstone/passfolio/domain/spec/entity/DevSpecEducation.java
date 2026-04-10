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
                        columnNames = {"dev_spec_id", "university_id"}
                )
        }
)
public class DevSpecEducation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dev_spec_id", nullable = false)
    private DevSpec devSpec;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id", nullable = false)
    private University university;

    /**
     * 이력 노출 순서(오름차순). 동일 스펙 내에서 학사 → 석사 → 박사 등 시간 순을 표현할 때 사용.
     */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
