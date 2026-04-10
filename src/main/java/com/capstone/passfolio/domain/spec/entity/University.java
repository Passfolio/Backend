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

    @Column(nullable = false, unique = true)
    private String domain;

    @Column(nullable = false)
    private String countryCode; // Ex. KR

    @Column(nullable = false)
    private String country; // Korea, Republic of
}