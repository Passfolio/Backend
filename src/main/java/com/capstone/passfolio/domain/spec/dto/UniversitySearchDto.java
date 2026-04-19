package com.capstone.passfolio.domain.spec.dto;

import com.capstone.passfolio.domain.spec.entity.UniversityDepartment;
import com.capstone.passfolio.domain.spec.repository.UniversityRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class UniversitySearchDto {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "대학교 후보 1건")
    public static class UniversityCandidateItem {
        @Schema(description = "대학교 UUID", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
        private String id;

        @Schema(description = "대학교명", example = "명지대학교")
        private String name;

        @Schema(description = "대학교 구분", example = "대학교")
        private String type;

        @Schema(description = "지역", example = "서울")
        private String region;

        @Schema(description = "입력 키워드 대비 유사도", example = "0.8125")
        private double similarity;

        public static UniversityCandidateItem from(UniversityRepository.UniversitySimilarityRow row) {
            return UniversityCandidateItem.builder()
                    .id(row.getId())
                    .name(row.getName())
                    .type(row.getType())
                    .region(row.getRegion())
                    .similarity(row.getSimilarity() == null ? 0.0d : row.getSimilarity())
                    .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "대학교 유사도 검색 응답")
    public static class UniversitySearchResponse {
        @Schema(description = "검색 키워드", example = "명지대")
        private String keyword;

        @Schema(description = "후보 대학교 목록(유사도 내림차순)")
        private List<UniversityCandidateItem> candidates;

        public static UniversitySearchResponse of(String keyword, List<UniversityCandidateItem> candidates) {
            return UniversitySearchResponse.builder()
                    .keyword(keyword)
                    .candidates(candidates)
                    .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "학과 상세 정보")
    public static class DepartmentDetailItem {
        @Schema(description = "university_department PK", example = "101")
        private Long id;

        @Schema(description = "학과명", example = "컴퓨터공학과")
        private String department;

        @Schema(description = "학위", example = "학사")
        private String degree;

        @Schema(description = "기간", example = "4년")
        private String duration;

        public static DepartmentDetailItem from(UniversityDepartment row) {
            return DepartmentDetailItem.builder()
                    .id(row.getId())
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
    @Schema(description = "학과 유사도 검색 응답")
    public static class DepartmentSearchResponse {
        @Schema(description = "대학교 UUID", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
        private String universityId;

        @Schema(description = "매칭된 학과명", example = "컴퓨터공학과")
        private String matchedDepartment;

        @Schema(description = "입력 키워드 대비 유사도", example = "0.7632")
        private double similarity;

        @Schema(description = "해당 학과의 모든 상세 row")
        private List<DepartmentDetailItem> items;

        public static DepartmentSearchResponse of(
                String universityId,
                String matchedDepartment,
                double similarity,
                List<DepartmentDetailItem> items) {
            return DepartmentSearchResponse.builder()
                    .universityId(universityId)
                    .matchedDepartment(matchedDepartment)
                    .similarity(similarity)
                    .items(items)
                    .build();
        }
    }
}
