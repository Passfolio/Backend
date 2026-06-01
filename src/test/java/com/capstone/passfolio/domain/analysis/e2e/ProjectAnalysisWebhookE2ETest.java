package com.capstone.passfolio.domain.analysis.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstone.passfolio.domain.analysis.entity.ProjectAnalysis;
import com.capstone.passfolio.domain.analysis.entity.enums.AnalysisFlag;
import com.capstone.passfolio.domain.analysis.repository.ProjectAnalysisRepository;
import com.capstone.passfolio.domain.user.entity.User;
import com.capstone.passfolio.domain.user.entity.enums.Role;
import com.capstone.passfolio.domain.user.repository.UserRepository;
import com.capstone.passfolio.support.AbstractIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 분석 완료 웹훅(POST /api/v1/project-analysis/webhook) E2E — 실제 보안 필터 체인 + 컨트롤러
 * + 서비스 + writer + H2까지의 전구간. X-INTERNAL-API-KEY 인증·404·멱등을 검증한다.
 * (배치 없는 단건 analysis로 외부 의존 미발생; RedissonClient/S3는 베이스에서 mock.)
 */
@AutoConfigureMockMvc
class ProjectAnalysisWebhookE2ETest extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/project-analysis/webhook";
    private static final String HEADER = "X-INTERNAL-API-KEY";
    private static final String KEY = "test-project-internal-key"; // application-test.yml과 일치

    @Autowired private MockMvc mockMvc;
    @Autowired private ProjectAnalysisRepository projectAnalysisRepository;
    @Autowired private UserRepository userRepository;

    private Long userId;

    @BeforeEach
    void seedUser() {
        User u = userRepository.saveAndFlush(User.builder()
                .nickname("tester").username("github_test").profileImageUrl("https://img/x.png")
                .githubLogin("tester").role(Role.USER).build());
        userId = u.getId();
    }

    @AfterEach
    void clean() {
        projectAnalysisRepository.deleteAll();
        userRepository.deleteAll();
    }

    private void seedAnalysis(String id) {
        projectAnalysisRepository.saveAndFlush(ProjectAnalysis.builder()
                .id(id).repoUrl("https://github.com/o/r")
                .user(userRepository.findById(userId).orElseThrow())
                .analysisFlag(AnalysisFlag.IN_PROGRESS).build());
    }

    private String body(String id, String status, String cdn) {
        return "{\"analysis_id\":\"" + id + "\",\"status\":\"" + status + "\""
                + (cdn == null ? "" : ",\"cdn_url\":\"" + cdn + "\"") + "}";
    }

    @Test
    @DisplayName("X-INTERNAL-API-KEY 누락 → 401 INTERNAL_API_UNAUTHORIZED")
    void missing_key_unauthorized() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body("a1", "analyzed", "https://cdn/x.json")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INTERNAL_API_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("잘못된 키 → 401")
    void wrong_key_unauthorized() throws Exception {
        mockMvc.perform(post(URL).header(HEADER, "wrong").contentType(MediaType.APPLICATION_JSON)
                        .content(body("a1", "analyzed", "https://cdn/x.json")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INTERNAL_API_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("올바른 키 + 없는 분석 → 404 PROJECT_ANALYSIS_NOT_FOUND")
    void valid_key_not_found() throws Exception {
        mockMvc.perform(post(URL).header(HEADER, KEY).contentType(MediaType.APPLICATION_JSON)
                        .content(body("missing", "analyzed", "https://cdn/x.json")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PROJECT_ANALYSIS_NOT_FOUND"));
    }

    @Test
    @DisplayName("올바른 키 + IN_PROGRESS 분석 → 200 + DB DONE + cdn 저장")
    void valid_key_marks_done() throws Exception {
        seedAnalysis("a1");

        mockMvc.perform(post(URL).header(HEADER, KEY).contentType(MediaType.APPLICATION_JSON)
                        .content(body("a1", "analyzed", "https://cdn/x.json")))
                .andExpect(status().isOk());

        ProjectAnalysis a = projectAnalysisRepository.findById("a1").orElseThrow();
        assertThat(a.getAnalysisFlag()).isEqualTo(AnalysisFlag.DONE);
        assertThat(a.getResultCdnUrl()).isEqualTo("https://cdn/x.json");
    }

    @Test
    @DisplayName("중복 콜백 → 둘 다 200, 상태 불변(멱등)")
    void idempotent_double_callback() throws Exception {
        seedAnalysis("a1");

        mockMvc.perform(post(URL).header(HEADER, KEY).contentType(MediaType.APPLICATION_JSON)
                .content(body("a1", "analyzed", "https://cdn/x.json"))).andExpect(status().isOk());
        // 두 번째: 실패 상태로 와도 이미 DONE이라 무시되어야 함
        mockMvc.perform(post(URL).header(HEADER, KEY).contentType(MediaType.APPLICATION_JSON)
                .content(body("a1", "failed", null))).andExpect(status().isOk());

        ProjectAnalysis a = projectAnalysisRepository.findById("a1").orElseThrow();
        assertThat(a.getAnalysisFlag()).isEqualTo(AnalysisFlag.DONE);
    }
}
