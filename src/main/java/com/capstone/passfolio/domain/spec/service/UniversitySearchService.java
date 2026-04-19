package com.capstone.passfolio.domain.spec.service;

import com.capstone.passfolio.domain.spec.dto.UniversitySearchDto;
import com.capstone.passfolio.domain.spec.entity.UniversityDepartment;
import com.capstone.passfolio.domain.spec.repository.UniversityDepartmentRepository;
import com.capstone.passfolio.domain.spec.repository.UniversityRepository;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UniversitySearchService {
    private final UniversityRepository universityRepository;
    private final UniversityDepartmentRepository universityDepartmentRepository;

    private static final double DEFAULT_UNIVERSITY_THRESHOLD = 0.1d;
    private static final double DEFAULT_DEPARTMENT_THRESHOLD = 0.1d;

    @Transactional(readOnly = true)
    public UniversitySearchDto.UniversitySearchResponse searchUniversity(
            String keyword,
            Double threshold) {
        String normalizedKeyword = normalizeKeyword(keyword);
        double normalizedThreshold = normalizeThreshold(threshold, DEFAULT_UNIVERSITY_THRESHOLD);

        List<UniversityRepository.UniversitySimilarityRow> matches = universityRepository
                .findTopMatchesByName(normalizedKeyword, normalizedThreshold);

        if (matches.isEmpty()) {
            throw new RestException(ErrorCode.DB_DATA_NOT_FOUND, "입력한 키워드와 유사한 대학교를 찾을 수 없습니다.");
        }

        List<UniversitySearchDto.UniversityCandidateItem> candidates = matches.stream()
                .map(UniversitySearchDto.UniversityCandidateItem::from)
                .toList();

        return UniversitySearchDto.UniversitySearchResponse.of(normalizedKeyword, candidates);
    }

    @Transactional(readOnly = true)
    public UniversitySearchDto.DepartmentSearchResponse searchDepartment(
            String universityId,
            String keyword,
            Double threshold) {
        String normalizedUniversityId = normalizeKeyword(universityId);
        String normalizedKeyword = normalizeKeyword(keyword);
        double normalizedThreshold = normalizeThreshold(threshold, DEFAULT_DEPARTMENT_THRESHOLD);

        UniversityDepartmentRepository.DepartmentSimilarityRow departmentMatch = universityDepartmentRepository
                .findBestDepartmentByUniversityIdAndKeyword(normalizedUniversityId, normalizedKeyword, normalizedThreshold)
                .orElseThrow(() -> new RestException(ErrorCode.DB_DATA_NOT_FOUND, "입력한 키워드와 유사한 학과를 찾을 수 없습니다."));

        List<UniversityDepartment> matchedRows = universityDepartmentRepository.findAllByUniversityIdAndDepartment(
                normalizedUniversityId,
                departmentMatch.getDepartment());

        if (matchedRows.isEmpty()) {
            throw new RestException(ErrorCode.DB_DATA_NOT_FOUND, "매칭된 학과 상세 데이터를 찾을 수 없습니다.");
        }

        return UniversitySearchDto.DepartmentSearchResponse.of(
                normalizedUniversityId,
                departmentMatch.getDepartment(),
                toSimilarityValue(departmentMatch.getSimilarity()),
                matchedRows.stream().map(UniversitySearchDto.DepartmentDetailItem::from).toList());
    }

    private String normalizeKeyword(String raw) {
        if (raw == null) {
            throw new RestException(ErrorCode.GLOBAL_BAD_REQUEST, "검색어는 필수입니다.");
        }
        String normalized = raw.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            throw new RestException(ErrorCode.GLOBAL_BAD_REQUEST, "검색어는 비어 있을 수 없습니다.");
        }
        return normalized;
    }

    private double normalizeThreshold(Double threshold, double defaultValue) {
        if (threshold == null) {
            return defaultValue;
        }
        if (threshold < 0.0d || threshold > 1.0d) {
            throw new RestException(ErrorCode.GLOBAL_BAD_REQUEST, "threshold는 0이상 1이하 값이어야 합니다.");
        }
        return threshold;
    }

    private double toSimilarityValue(Double similarity) { return similarity == null ? 0.0d : similarity; }

}
