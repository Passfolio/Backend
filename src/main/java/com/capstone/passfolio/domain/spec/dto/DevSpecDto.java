package com.capstone.passfolio.domain.spec.dto;

import com.capstone.passfolio.domain.spec.entity.Career;
import com.capstone.passfolio.domain.spec.entity.DevSpec;
import com.capstone.passfolio.domain.spec.entity.University;
import com.capstone.passfolio.domain.spec.entity.UniversityDepartment;
import com.capstone.passfolio.domain.spec.entity.enums.CareerTag;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.validator.constraints.UniqueElements;

import java.util.ArrayList;
import java.util.List;

public class DevSpecDto {
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "개발자 역량 업데이트 요청")
    public static class UpdateRequest {
        @Min(0) @Max(50)
        @Schema(description = "경력 연차", example = "1")
        private int experience;

        @NotNull
        @UniqueElements(message = "universityDepartmentIds에 중복된 값이 있습니다.")
        @Size(max = 5, message = "universityDepartmentIds는 최대 5개까지 입력할 수 있습니다.")
        @Schema(
                description = "학력 이력: university_department PK 목록. 배열 앞쪽이 display_order가 작은 항목입니다. 생략 시 빈 배열.",
                example = "[101, 202, 303]")
        private List<@NotNull(message = "universityDepartmentIds 요소는 null일 수 없습니다.") Long> universityDepartmentIds;

        @NotNull
        @UniqueElements(message = "careerIds에 중복된 값이 있습니다.")
        @Size(max = 200, message = "careerIds는 최대 200개까지 입력할 수 있습니다.")
        @Schema(description = "직무 정보 아이디 리스트. 생략 시 빈 배열.", example = "['uuid1', 'uuid2', ...]")
        private List<@NotBlank(message = "careerIds 요소는 비어 있을 수 없습니다.") String> careerIds;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "학력 이력 한 줄 (학교·학과·학력 확정)")
    public static class EducationHistoryItem {
        @Schema(description = "university_department PK (클라이언트가 저장 시 전달)", example = "42")
        private Long universityDepartmentId;

        @Schema(description = "학교 인스턴스 UUID (univ_uuid_pk)", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
        private String universityId;

        @Schema(description = "대학교명", example = "명지대학교")
        private String name;

        @Schema(description = "대학교 타입", example = "대학교, 전문대학교, 대학원, ...")
        private String type;

        @Schema(description = "시/도", example = "경기")
        private String region;

        @Schema(description = "학과명", example = "컴퓨터공학과")
        private String department;

        @Schema(description = "학위", example = "학사, 석사, ...")
        private String degree;

        @Schema(description = "학력 기간", example = "4년")
        private String duration;

        public static EducationHistoryItem from(UniversityDepartment row) {
            if (row == null) { return null; }

            University u = row.getUniversity();
            if (u == null) { return null; }

            return EducationHistoryItem.builder()
                    .universityDepartmentId(row.getId())
                    .universityId(u.getId())
                    .name(u.getName())
                    .type(u.getType())
                    .region(u.getRegion())
                    .department(row.getDepartment())
                    .degree(row.getDegree())
                    .duration(row.getDuration())
                    .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "직무 정보")
    public static class CareerInfo {
        @Schema(description = "경력", example = "3년")
        private int experience;

        @Schema(description = "직무 키워드 리스트", example = "백엔드")
        private List<String> careerKeywords;

        @Schema(description = "전문 분야 리스트", example = "검색엔진")
        private List<String> careerMajors;

        @Schema(description = "기술 스택 리스트", example = "Spring Boot")
        private List<String> careerSkills;

        public static CareerInfo of(
                List<String> careerKeywords,
                List<String> careerMajors,
                List<String> careerSkills) {

            return CareerInfo.builder()
                    .careerKeywords(careerKeywords)
                    .careerMajors(careerMajors)
                    .careerSkills(careerSkills)
                    .build();
        }

        public static CareerInfo from(int experience, List<Career> careers) {
            List<String> roles = new ArrayList<>();
            List<String> majors = new ArrayList<>();
            List<String> skills = new ArrayList<>();
            for (Career c : careers) {
                CareerTag tag = c.getCareerTag();
                if (tag == null) {
                    continue;
                }
                switch (tag) {
                    case ROLE -> roles.add(c.getCareerKeyword());
                    case MAJOR -> majors.add(c.getCareerKeyword());
                    case SKILL -> skills.add(c.getCareerKeyword());
                }
            }
            return CareerInfo.builder()
                    .experience(experience)
                    .careerKeywords(roles)
                    .careerMajors(majors)
                    .careerSkills(skills)
                    .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "개발 스펙 업데이트 응답")
    public static class UpdateResponse {
        @Schema(description = "학력 이력(시간/노출 순서대로)")
        private List<EducationHistoryItem> educationHistory;

        @Schema(description = "보유 직무 역량 목록")
        private List<CareerInfo> careers;

        public static UpdateResponse from(DevSpec devSpec, List<Career> orderedCareers) {
            List<EducationHistoryItem> educationHistory = devSpec.getDevSpecEducations().stream()
                    .map(e -> EducationHistoryItem.from(e.getUniversityDepartment()))
                    .toList();
            return UpdateResponse.builder()
                    .educationHistory(educationHistory)
                    .careers(List.of(CareerInfo.from(devSpec.getExperience(), orderedCareers)))
                    .build();
        }
    }
}
