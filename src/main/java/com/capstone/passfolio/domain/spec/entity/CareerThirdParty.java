package com.capstone.passfolio.domain.spec.entity;

import com.capstone.passfolio.domain.spec.entity.enums.ThirdParty;
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
        name = "career_third_party",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_CAREER_THIRD_PARTY",
                        columnNames = {"career_code", "third_party"}
                )
        }
)
public class CareerThirdParty {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String careerCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    ThirdParty thirdParty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_id", nullable = false, updatable = false)
    private Career career;
}
