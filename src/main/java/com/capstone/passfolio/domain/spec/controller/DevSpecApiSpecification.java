package com.capstone.passfolio.domain.spec.controller;

import com.capstone.passfolio.domain.spec.dto.DevSpecDto;
import com.capstone.passfolio.system.exception.dto.ErrorResponse;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;

import java.util.List;

@Tag(
        name = "DevSpec",
        description = "로그인 사용자 개발 스펙(학력·직무) API")
@SecurityRequirement(name = "bearerAuth")
public interface DevSpecApiSpecification {

    @Operation(
            summary = "개발 스펙 조회",
            description = """
                    현재 로그인한 사용자의 `dev_spec` 스냅샷을 반환합니다.

                    - `dev_spec` 행이 없으면 경력 0, 빈 배열과 동일한 기본값을 돌려줍니다.
                    - 저장에 필요한 id 목록(`universityDepartmentIds`, `careerIds`)은 **PATCH 요청**에만 보냅니다. 조회 응답에는 포함되지 않습니다.
                    - `educationHistory`는 학교·학과 등 표시용 필드만 포함합니다.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "조회 성공",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = DevSpecDto.UpdateResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "인증 실패",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "DB에 사용자 행이 없을 때 (`USER_NOT_FOUND`)",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    DevSpecDto.UpdateResponse getMyDevSpec(@Parameter(hidden = true) UserPrincipal userPrincipal);

    @Operation(
            summary = "학력 이력 조회",
            description = """
                    로그인 사용자의 학력(`dev_spec_education` + `university_department`)만 시간 순으로 반환합니다.

                    - `dev_spec`이 없으면 빈 배열 `[]`입니다.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "조회 성공",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        array = @ArraySchema(schema = @Schema(implementation = DevSpecDto.EducationHistoryItem.class)))),
                @ApiResponse(
                        responseCode = "401",
                        description = "인증 실패",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "`USER_NOT_FOUND`",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    List<DevSpecDto.EducationHistoryItem> getMyEducationHistory(
            @Parameter(hidden = true) UserPrincipal userPrincipal);

    @Operation(
            summary = "직무(Career) 조회",
            description = """
                    로그인 사용자의 경력 연차와 선택한 career 키워드(역할·전공·스킬 태그별)를 반환합니다.

                    - `dev_spec`이 없으면 연차 0, 빈 키워드 목록과 동일한 값입니다.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "조회 성공",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = DevSpecDto.CareerReadResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "인증 실패",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "`USER_NOT_FOUND`",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    DevSpecDto.CareerReadResponse getMyCareer(@Parameter(hidden = true) UserPrincipal userPrincipal);

    @Operation(
            summary = "개발 스펙 업데이트",
            description = """
                    현재 로그인한 사용자에 대해 `dev_spec` 행을 **없으면 생성, 있으면 전체 치환**합니다.

                    ## 프론트엔드에서 꼭 알 것
                    - 요청 본문은 **항상 세 필드를 포함**하세요. `universityDepartmentIds`, `careerIds`는 JSON에서 **`null`이면 안 됩니다.** 비어 있으면 `[]`를 보냅니다.
                    - 학력·직무를 비우려면 `[]`를 보내며, 필드를 생략하면 Bean Validation에서 `400`이 납니다.
                    - `universityDepartmentIds` 배열 **앞쪽이 화면 노출 순서**(서버 `display_order` 0부터)입니다.
                    - `careerIds`는 career 시드 데이터의 **career PK(UUID 문자열)**입니다. 서버가 `ROLE` / `MAJOR` / `SKILL`로 나눠 응답에 채웁니다.

                    ## 인증
                    - 유효한 액세스 토큰(쿠키/헤더 정책은 환경과 동일)이 필요합니다.

                    ## 요청 본문 필드

                    | 필드 | 타입 | 필수 | 제약 | 설명 |
                    |------|------|------|------|------|
                    | `experience` | number | 예 | 0 이상 50 이하 | 경력 연차(정수) |
                    | `universityDepartmentIds` | number[] | 예 (`null` 불가) | 최대 5개, 요소 `null` 불가, 중복 불가 | `university_department.id` 목록. 앞이 먼저 노출 |
                    | `careerIds` | string[] | 예 (`null` 불가) | 최대 200개, 요소 빈 문자열 불가, 중복 불가 | `career.id`(UUID) 목록 |

                    ## 비즈니스 검증 (DTO 통과 후)
                    - `universityDepartmentIds`·`careerIds`에 적은 id가 DB에 없으면 `400` + 메시지로 구분됩니다.

                    ## 응답
                    - 표시용 스냅샷만 반환합니다. `experience`는 최상위에 한 번, 직무 키워드는 `careers`에 태그별로 묶입니다.
                    - id 배열은 응답에 포함하지 않습니다. 수정 시에는 PATCH 요청에 `universityDepartmentIds`, `careerIds`를 다시 보냅니다.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "저장 성공. 저장된 스냅샷을 반환합니다.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = DevSpecDto.UpdateResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "성공 응답 예시",
                                                        description = "careers는 태그별로 묶인 키워드 문자열 배열입니다.",
                                                        value =
                                                                """
                                                                {
                                                                  "experience": 3,
                                                                  "educationHistory": [
                                                                    {
                                                                      "name": "명지대학교",
                                                                      "type": "대학교",
                                                                      "region": "서울특별시",
                                                                      "department": "컴퓨터공학과",
                                                                      "degree": "학사",
                                                                      "duration": "4년"
                                                                    }
                                                                  ],
                                                                  "careers": [
                                                                    {
                                                                      "careerKeywords": ["백엔드 개발자"],
                                                                      "careerMajors": ["검색엔진"],
                                                                      "careerSkills": ["Spring Boot", "Java"]
                                                                    }
                                                                  ]
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "400",
                        description = """
                                잘못된 요청

                                | 구분 | 예시 상황 | 비고 |
                                |------|------------|------|
                                | Bean Validation | `universityDepartmentIds`를 `null`로 보냄, 배열 요소 `null`, 중복 id, `careerIds` 빈 문자열, `experience` 범위 초과 | 보통 `GLOBAL_BAD_REQUEST` 또는 필드 메시지 |
                                | 존재하지 않는 참조 | DB에 없는 `university_department` id / `career` id | 메시지에 id 포함 |
                                """,
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples = {
                                            @ExampleObject(
                                                    name = "Validation — null 배열",
                                                    summary = "NotNull: 리스트 필드 누락",
                                                    value =
                                                            """
                                                            {
                                                              "status": "BAD_REQUEST",
                                                              "error": "GLOBAL_BAD_REQUEST",
                                                              "message": "universityDepartmentIds : must not be null"
                                                            }
                                                            """),
                                            @ExampleObject(
                                                    name = "Validation — 중복 학과 id",
                                                    summary = "UniqueElements",
                                                    value =
                                                            """
                                                            {
                                                              "status": "BAD_REQUEST",
                                                              "error": "GLOBAL_BAD_REQUEST",
                                                              "message": "universityDepartmentIds에 중복된 값이 있습니다."
                                                            }
                                                            """),
                                            @ExampleObject(
                                                    name = "존재하지 않는 university_department",
                                                    summary = "서비스 검증",
                                                    value =
                                                            """
                                                            {
                                                              "status": "BAD_REQUEST",
                                                              "error": "GLOBAL_BAD_REQUEST",
                                                              "message": "존재하지 않는 university_department id 입니다: 999999999"
                                                            }
                                                            """)
                                        })),
                @ApiResponse(
                        responseCode = "401",
                        description = """
                                인증 실패

                                | ErrorCode | 상황 |
                                |-----------|------|
                                | `JWT_MISSING` | 토큰 없음 |
                                | `JWT_EXPIRED` | 만료 |
                                | `JWT_INVALID` | 형식/서명 오류 |
                                """,
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "JWT_EXPIRED",
                                                        value =
                                                                """
                                                                {
                                                                  "status": "UNAUTHORIZED",
                                                                  "error": "JWT_EXPIRED",
                                                                  "message": "만료된 토큰입니다."
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "404",
                        description = "토큰은 유효하나 DB에 사용자 행이 없을 때 (`USER_NOT_FOUND`)",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "USER_NOT_FOUND",
                                                        value =
                                                                """
                                                                {
                                                                  "status": "NOT_FOUND",
                                                                  "error": "USER_NOT_FOUND",
                                                                  "message": "존재하지 않는 사용자입니다."
                                                                }
                                                                """)))
            })
    DevSpecDto.UpdateResponse updateDevSpec(
            @Parameter(hidden = true) UserPrincipal userPrincipal,
            @RequestBody(
                    description = """
                            **`universityDepartmentIds`와 `careerIds`는 반드시 배열로 보내세요.** 생략하면 안 되고, 빈 값은 `[]`입니다.

                            | 필드 | JSON 타입 | 비고 |
                            |------|------------|------|
                            | experience | number | 0~50 정수 |
                            | universityDepartmentIds | array of number | 최대 5개, 중복·null 요소 불가 |
                            | careerIds | array of string (UUID) | 최대 200개, 빈 문자열 불가, 중복 불가 |
                            """,
                    required = true,
                    content =
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = DevSpecDto.UpdateRequest.class),
                                    examples = {
                                        @ExampleObject(
                                                name = "학력·직무 모두 비움",
                                                value =
                                                        """
                                                        {
                                                          "experience": 0,
                                                          "universityDepartmentIds": [],
                                                          "careerIds": []
                                                        }
                                                        """),
                                        @ExampleObject(
                                                name = "학력 2건 + career 다건",
                                                value =
                                                        """
                                                        {
                                                          "experience": 3,
                                                          "universityDepartmentIds": [101, 202],
                                                          "careerIds": [
                                                            "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                                                            "b2c3d4e5-f6a7-8901-bcde-f12345678901"
                                                          ]
                                                        }
                                                        """)
                                    }))
            @Valid
            DevSpecDto.UpdateRequest request);
}
