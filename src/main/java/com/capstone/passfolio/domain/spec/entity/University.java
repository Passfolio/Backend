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
@Table(name = "university")
public class University {
    // TODO: Change to Snowflake
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // 대학교명

    @Column(nullable = false)
    private String educationType; // 학제

    @Column(nullable = false)
    private String campusType; // 본분교

    @Column(nullable = false)
    private String region; // 시/도

    @Column(nullable = false)
    private String departmentName; // 학과명

    @Column(nullable = false)
    private int educationLevelCode; // 학력코드

    @Column(nullable = false)
    private String educationLevel; // 학력

    @Column(nullable = false, unique = true)
    private String pageUrl;
}