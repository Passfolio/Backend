package com.capstone.passfolio.domain.spec.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 학교 인스턴스({@link University}) 소속 학과·학력 한 줄. 클라이언트는 이 엔티티의 PK로 학력을 지정한다.
 */
@Builder
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "university_department")
public class UniversityDepartment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id", nullable = false, updatable = false)
    private University university;

    @Column(nullable = false, updatable = false)
    private String department;

    @Column(nullable = false, updatable = false)
    private String degree;

    @Column(nullable = false, updatable = false)
    private String duration;
}
