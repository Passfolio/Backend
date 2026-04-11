package com.capstone.passfolio.domain.spec.entity;

import com.capstone.passfolio.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Builder
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "dev_spec")
public class DevSpec {
    @Id @Column(name = "user_id")
    private Long id; // user_id 그대로 위임해서 1:1 Mapping 관계 구축 (Shared PK)

    @Builder.Default
    @Column(nullable = false)
    private int experience = 0; // 경력 ex. 경력 1년차 -> experience=1

    // ----- Relation Area ----- //

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder.Default
    @OneToMany(mappedBy = "devSpec", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<DevSpecEducation> devSpecEducations = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "devSpec", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DevSpecCareer> devSpecCareers = new ArrayList<>();
}
