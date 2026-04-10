package com.capstone.passfolio.domain.spec.dto;

import com.capstone.passfolio.domain.spec.entity.DevSpec;
import com.capstone.passfolio.domain.spec.entity.DevSpecEducation;
import com.capstone.passfolio.domain.spec.entity.DevSpecJob;
import com.capstone.passfolio.domain.spec.entity.Job;
import com.capstone.passfolio.domain.spec.entity.University;
import com.capstone.passfolio.domain.spec.entity.enums.JobTag;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.Collections;
import java.util.List;

public class DevSpecDto {
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "개발자 역량 업데이트 요청")
    public static class DevSpecUpdateRequest {
        @Schema(description = "경력 연차", example = "1")
        private Integer experience;

        @Schema(
                description = "학력 이력: university 테이블 PK 목록. 배열 앞쪽이 display_order가 작은 항목(먼저 경험한 학력 등)으로 저장됩니다.",
                example = "[1, 2, 3]")
        private List<Long> educationUniversityIds;
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

        @Schema(description = "전형/모집단위 페이지 URL", example = "https://example.com/admission")
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
    public static class JobInfo {
        @Schema(description = "직무 코드", example = "84")
        private int jobCode;

        @Schema(description = "직무 키워드", example = "백엔드/서버개발")
        private String jobKeyword;

        @Schema(description = "직무 태그", example = "ROLE")
        private JobTag jobTag;

        public static JobInfo from(Job job) {
            if (job == null) { return null; }

            return JobInfo.builder()
                    .jobCode(job.getJobCode())
                    .jobKeyword(job.getJobKeyword())
                    .jobTag(job.getJobTag())
                    .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "개발 스펙 응답")
    public static class DevSpecResponse {
        @Schema(description = "경력 연차", example = "1")
        private int experience;

        @Schema(description = "학력 이력(시간/노출 순서대로)")
        private List<UniversityInfo> educationHistory;

        @Schema(description = "보유 직무 역량 목록")
        private List<JobInfo> jobs;

        public static DevSpecResponse from(DevSpec devSpec) {
            if (devSpec == null) { return null; }

            List<JobInfo> jobInfos = devSpec.getDevSpecJobs() == null
                    ? Collections.emptyList()
                    : devSpec.getDevSpecJobs()
                    .stream()
                    .map(DevSpecJob::getJob)
                    .map(JobInfo::from)
                    .toList();

            List<UniversityInfo> educationHistory = devSpec.getDevSpecEducations() == null
                    ? Collections.emptyList()
                    : devSpec.getDevSpecEducations()
                    .stream()
                    .map(DevSpecEducation::getUniversity)
                    .map(UniversityInfo::from)
                    .toList();

            return DevSpecResponse.builder()
                    .experience(devSpec.getExperience())
                    .educationHistory(educationHistory)
                    .jobs(jobInfos)
                    .build();
        }
    }
}
