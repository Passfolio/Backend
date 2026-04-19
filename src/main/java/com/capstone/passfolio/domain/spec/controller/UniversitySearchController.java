package com.capstone.passfolio.domain.spec.controller;

import com.capstone.passfolio.domain.spec.dto.UniversitySearchDto;
import com.capstone.passfolio.domain.spec.service.UniversitySearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/spec/search")
public class UniversitySearchController implements UniversitySearchApiSpecification {

    private final UniversitySearchService universitySearchService;

    @Override
    @GetMapping("/universities")
    public UniversitySearchDto.UniversitySearchResponse searchUniversity(
            @RequestParam("q") String keyword,
            @RequestParam(value = "threshold", required = false) Double threshold) {
        return universitySearchService.searchUniversity(keyword, threshold);
    }

    @Override
    @GetMapping("/departments")
    public UniversitySearchDto.DepartmentSearchResponse searchDepartment(
            @RequestParam("univ_uuid") String universityId,
            @RequestParam("q") String keyword,
            @RequestParam(value = "threshold", required = false) Double threshold) {
        return universitySearchService.searchDepartment(universityId, keyword, threshold);
    }
}
