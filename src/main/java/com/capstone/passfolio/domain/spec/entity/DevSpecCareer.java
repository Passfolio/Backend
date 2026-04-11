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
        name = "dev_spec_job",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_DEV_SPEC_JOB",
                        columnNames = {"dev_spec_id", "job_id"}
                )
        }
)
public class DevSpecJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dev_spec_id", nullable = false)
    private DevSpec devSpec;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;
}
