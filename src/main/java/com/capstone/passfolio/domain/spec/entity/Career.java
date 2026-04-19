package com.capstone.passfolio.domain.spec.entity;

import com.capstone.passfolio.domain.spec.entity.enums.CareerTag;
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
        name = "career",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_CAREER_KEYWORD_TAG",
                        columnNames = {"career_keyword", "career_tag"}
                )
        }
)
public class Career {
    @Id
    private String id; // DataInitializer -> UuidGenerator.generate(careerTag:careerKeyword)

    @Column(nullable = false, updatable = false)
    private String careerKeyword;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    CareerTag careerTag; // 3 정규화를 하는게 맞긴한데, 또 너무 쪼개버리면 Join 비용이 있으니까 타협함
}