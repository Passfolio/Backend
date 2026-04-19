package com.capstone.passfolio.domain.spec.controller;

import com.capstone.passfolio.domain.spec.dto.UniversitySearchDto;
import com.capstone.passfolio.system.exception.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;

@Tag(
        name = "University Search",
        description = "대학교/학과 유사도 검색 API")
public interface UniversitySearchApiSpecification {

    @Operation(
            summary = "대학교 검색",
            description = "입력된 대학교명과 유사한 대학교 후보를 pg_trgm 유사도 기준으로 반환합니다.")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "대학교 검색 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UniversitySearchDto.UniversitySearchResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 파라미터",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "status": "BAD_REQUEST",
                                      "error": "GLOBAL_BAD_REQUEST",
                                      "message": "검색어는 비어 있을 수 없습니다."
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "404",
                    description = "유사한 대학교를 찾지 못함",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "status": "NOT_FOUND",
                                      "error": "DB_DATA_NOT_FOUND",
                                      "message": "입력한 키워드와 유사한 대학교를 찾을 수 없습니다."
                                    }
                                    """)))
    })
    UniversitySearchDto.UniversitySearchResponse searchUniversity(
            @Parameter(description = "검색할 대학교명", required = true, example = "명지대")
            @NotBlank(message = "q는 필수이며 비어 있을 수 없습니다.")
            String keyword,
            @Parameter(description = "유사도 하한(0~1), 미입력시 0.10", required = false, example = "0.1")
            Double threshold);

    @Operation(
            summary = "학과 검색",
            description = "대학교 UUID 범위에서 입력 학과명과 유사한 학과를 1건 식별한 뒤, 해당 학과의 모든 상세 row를 반환합니다.")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "학과 검색 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UniversitySearchDto.DepartmentSearchResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 파라미터",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "유사한 학과를 찾지 못함",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    UniversitySearchDto.DepartmentSearchResponse searchDepartment(
            @Parameter(description = "대학교 UUID", required = true, example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
            @NotBlank(message = "univ_uuid는 필수이며 비어 있을 수 없습니다.")
            String universityId,
            @Parameter(description = "검색할 학과명", required = true, example = "컴공")
            @NotBlank(message = "q는 필수이며 비어 있을 수 없습니다.")
            String keyword,
            @Parameter(description = "유사도 하한(0~1), 미입력시 0.10", required = false, example = "0.1")
            Double threshold);
}
