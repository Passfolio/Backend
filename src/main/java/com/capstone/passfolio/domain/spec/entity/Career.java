package com.capstone.passfolio.domain.spec.entity;

import com.capstone.passfolio.domain.spec.entity.enums.JobTag;
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
        name = "job",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_JOB_KEYWORD_TAG",
                        columnNames = {"job_keyword", "job_tag"}
                )
        }
)
public class Job {
    @Id
    private String id; // DataInitializer -> UuidGenerator.generate(jobTag:jobKeyword)

    @Column(nullable = false, updatable = false)
    private String jobKeyword;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    JobTag jobTag; // 3 정규화를 하는게 맞긴한데, 또 너무 쪼개버리면 Join 비용이 있으니까 타협함
}