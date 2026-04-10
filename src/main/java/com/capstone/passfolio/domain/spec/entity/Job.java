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
@Table(name = "job")
public class Job {
    // TODO: Change to Snowflake
    @Id @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id; // 왜 직무 코드 안쓰죠? -> 채용 플랫폼 별 지정 직무코드가 다를 수도 있음 -> 유지보수 비용 증가함

    @Column(nullable = false, unique = true)
    private int jobCode;

    @Column(nullable = false, unique = true)
    private String jobKeyword;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    JobTag jobTag; // 3 정규화를 하는게 맞긴한데, 또 너무 쪼개버리면 Join 비용이 있으니까 타협함
}
