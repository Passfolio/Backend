package com.capstone.passfolio.domain.spec.dto;

import com.capstone.passfolio.domain.spec.entity.DevSpec;
import com.capstone.passfolio.domain.spec.entity.DevSpecJob;
import com.capstone.passfolio.domain.spec.entity.Job;
import com.capstone.passfolio.domain.spec.entity.University;
import com.capstone.passfolio.domain.spec.entity.enums.JobTag;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

@Slf4j
public class DevSpecDto {
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "대학교 정보")
    public static class UniversityInfo {
        @Schema(description = "대학교명", example = "명지대학교")
        private String name;

        @Schema(description = "대학교 도메인", example = "mju.ac.kr")
        private String domain;

        @Schema(description = "국가 코드", example = "KR")
        private String countryCode;

        @Schema(description = "국가", example = "Korea, Republic of")
        private String country;

        public static UniversityInfo from(University university) {
            if (university == null) { return null; }

            return UniversityInfo.builder()
                    .name(university.getName())
                    .domain(university.getDomain())
                    .countryCode(university.getCountryCode())
                    .country(university.getCountry())
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

        @Schema(description = "대학교 정보")
        private UniversityInfo university;

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

            return DevSpecResponse.builder()
                    .experience(devSpec.getExperience())
                    .university(UniversityInfo.from(devSpec.getUniversity()))
                    .jobs(jobInfos)
                    .build();
        }
    }
}
