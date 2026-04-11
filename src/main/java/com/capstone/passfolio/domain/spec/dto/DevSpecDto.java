package com.capstone.passfolio.domain.spec.dto;

import com.capstone.passfolio.domain.spec.entity.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

public class DevSpecDto {
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "개발자 역량 업데이트 요청")
    public static class UpdateRequest {
        @Schema(description = "경력 연차", example = "1")
        private int experience;

        @Schema(
                description = "학력 이력: university 테이블 PK 목록. 배열 앞쪽이 display_order가 작은 항목(먼저 경험한 학력 등)으로 저장됩니다.",
                example = "[1, 2, 3]")
        private List<Long> educationUniversityIds; // TODO: 대학교 검색 기능이 선행되어야 함

        @Schema(description = "직무 정보 아이디 리스트", example = "['uuid1', 'uuid2', ...]")
        private List<String> careerIds;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "대학교 정보")
    public static class UniversityInfo {
        @Schema(description = "대학교 ID", example = "1")
        private Long id;

        @Schema(description = "대학교명", example = "명지대학교")
        private String name;

        @Schema(description = "학제", example = "4년제")
        private String educationType;

        @Schema(description = "본분교", example = "본교")
        private String campusType;

        @Schema(description = "시/도", example = "경기")
        private String region;

        @Schema(description = "학과명", example = "컴퓨터공학과")
        private String departmentName;

        @Schema(description = "학력코드", example = "0")
        private int educationLevelCode;

        @Schema(description = "학력", example = "학사")
        private String educationLevel;

        @Schema(description = "학교 홈페이지", example = "https://mju.ac.kr")
        private String pageUrl;

        public static UniversityInfo from(University university) {
            if (university == null) { return null; }

            return UniversityInfo.builder()
                    .id(university.getId())
                    .name(university.getName())
                    .educationType(university.getEducationType())
                    .campusType(university.getCampusType())
                    .region(university.getRegion())
                    .departmentName(university.getDepartmentName())
                    .educationLevelCode(university.getEducationLevelCode())
                    .educationLevel(university.getEducationLevel())
                    .pageUrl(university.getPageUrl())
                    .build();
        }

        public University toEntity() {
            return University.builder()
                    .name(name)
                    .educationType(educationType)
                    .campusType(campusType)
                    .region(region)
                    .departmentName(departmentName)
                    .educationLevelCode(educationLevelCode)
                    .educationLevel(educationLevel)
                    .pageUrl(pageUrl)
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
                    .careerSkills(careerMajors)
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
        private List<UniversityInfo> educationHistory;

        @Schema(description = "보유 직무 역량 목록")
        private List<CareerInfo> careers;
    }
}
