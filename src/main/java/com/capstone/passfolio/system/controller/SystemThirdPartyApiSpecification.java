package com.capstone.passfolio.system.controller;

import com.capstone.passfolio.domain.thirdparty.govdata.UniversityOpenApiTrigger;
import com.capstone.passfolio.system.exception.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(
        name = "System — Third party",
        description =
                """
                운영·배치 보조용 서드파티 연동 API

                ## 보안
                - 현재 Security 설정상 `/api/v1/system/**` 는 **비인증(permitAll)** 로 열려 있습니다.
                - 프로덕션에서는 네트워크 경계·별도 인증·Swagger 비활성화 등 **추가 통제**를 강력히 권장합니다.

                ## 공통
                - 외부 공공 API·AWS Step Functions 등 **외부 의존성**을 호출합니다.
                - 예외 시 스프링 기본 예외 처리에 따라 4xx/5xx 및 `ErrorResponse`가 반환될 수 있습니다.

                Swagger UI·OpenAPI 문서에서는 **숨김(hidden)** 처리됩니다.
                """)
@Hidden
public interface SystemThirdPartyApiSpecification {

    @Operation(
            hidden = true,
            summary = "대학·학과 공공 API 프로브 (연결·YR·totalCount)",
            description =
                    """
                    외부 공공데이터포털 API를 **1페이지만** 호출하는 읽기·멱등 프로브입니다.

                    ## 목적
                    1. `serviceKey`·엔드포인트 가용성 확인
                    2. 사용할 학년도 `YR` 결정 (최근 연도부터 역순 시도)
                    3. `totalCount` 확보로 이후 전체 수집 페이지 수 추정

                    ## 응답
                    - `yr`: 실제로 데이터가 확인된 학년도 문자열
                    - `totalCount`: 해당 `YR`에 대한 전체 건수

                    ## 실패
                    - `serviceKey` 미설정·외부 API 실패·`totalCount` 파싱 실패 등으로 `IllegalStateException` 등이 전파될 수 있습니다.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "프로브 성공",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                UniversityOpenApiTrigger.UniversityOpenApiProbeResult.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "프로브 결과",
                                                        value =
                                                                """
                                                                {
                                                                  "yr": "2025",
                                                                  "totalCount": 12345
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "500",
                        description = "설정 누락·외부 API 실패·YR 탐색 실패 등",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "GLOBAL_INTERNAL_SERVER_ERROR",
                                                        value =
                                                                """
                                                                {
                                                                  "status": "INTERNAL_SERVER_ERROR",
                                                                  "error": "GLOBAL_INTERNAL_SERVER_ERROR",
                                                                  "message": "서버 내부에 오류가 발생했습니다."
                                                                }
                                                                """)))
            })
    ResponseEntity<UniversityOpenApiTrigger.UniversityOpenApiProbeResult> probeUniversityOpenApi();

    @Operation(
            hidden = true,
            summary = "대학·학과 공공 API 수집 Step Functions 실행",
            description =
                    """
                    1) 내부적으로 `probe`와 동일한 로직으로 `YR`·`totalCount`를 확보하고
                    2) 페이지 단위 Lambda 입력 페이로드를 생성한 뒤
                    3) Step Functions 실행을 요청합니다.

                    ## 응답
                    - **202 Accepted**: 비동기 작업 수락, 본문 없음

                    ## 실패
                    - 프로브 단계에서 동일하게 실패할 수 있으며, Step Functions 호출 실패 시에도 5xx가 날 수 있습니다.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "202", description = "Step Functions 시작 요청 수락 (본문 없음)"),
                @ApiResponse(
                        responseCode = "500",
                        description = "프로브·페이로드 생성·Step Functions 호출 중 오류",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "GLOBAL_INTERNAL_SERVER_ERROR",
                                                        value =
                                                                """
                                                                {
                                                                  "status": "INTERNAL_SERVER_ERROR",
                                                                  "error": "GLOBAL_INTERNAL_SERVER_ERROR",
                                                                  "message": "서버 내부에 오류가 발생했습니다."
                                                                }
                                                                """)))
            })
    ResponseEntity<Void> triggerStepFunction();
}
