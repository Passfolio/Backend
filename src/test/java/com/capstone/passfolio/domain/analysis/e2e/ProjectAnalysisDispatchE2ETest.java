package com.capstone.passfolio.domain.analysis.e2e;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstone.passfolio.domain.analysis.repository.ProjectAnalysisRepository;
import com.capstone.passfolio.domain.analysis.service.AnalysisAdmissionPacer;
import com.capstone.passfolio.domain.analysis.service.AnalysisTokenPreparer;
import com.capstone.passfolio.domain.analysis.service.BatchPhoneStore;
import com.capstone.passfolio.domain.analysis.service.BatchProgressTracker;
import com.capstone.passfolio.domain.aws.sqs.SqsMessageSender;
import com.capstone.passfolio.domain.github.client.GitHubApiClient;
import com.capstone.passfolio.domain.github.dto.GitHubDto;
import com.capstone.passfolio.domain.user.entity.User;
import com.capstone.passfolio.domain.user.entity.enums.Role;
import com.capstone.passfolio.domain.user.repository.UserRepository;
import com.capstone.passfolio.support.AbstractIntegrationTest;
import com.capstone.passfolio.system.security.model.UserPrincipal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 분석 디스패치(POST /api/v1/project-analysis/start) E2E — JWT 인증 + Bean Validation(@Size≤3)
 * + 컨트롤러 + initiateBatch + writer(H2)까지. 외부 협력자(토큰/GitHub/SQS/admission/배치/phone)는 mock.
 */
@AutoConfigureMockMvc
class ProjectAnalysisDispatchE2ETest extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/project-analysis/start";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectAnalysisRepository projectAnalysisRepository;

    @MockitoBean private AnalysisTokenPreparer tokenPreparer;
    @MockitoBean private GitHubApiClient gitHubApiClient;
    @MockitoBean private SqsMessageSender sqsMessageSender;
    @MockitoBean private AnalysisAdmissionPacer admissionPacer;
    @MockitoBean private BatchProgressTracker batchProgressTracker;
    @MockitoBean private BatchPhoneStore batchPhoneStore;

    private Long userId;

    @BeforeEach
    void seed() {
        User u = userRepository.saveAndFlush(User.builder()
                .nickname("tester").username("github_test").profileImageUrl("https://img/x.png")
                .githubLogin("octocat").role(Role.USER).build());
        userId = u.getId();
    }

    @AfterEach
    void clean() {
        projectAnalysisRepository.deleteAll();
        userRepository.deleteAll();
    }

    private RequestPostProcessor auth() {
        UserPrincipal principal = UserPrincipal.builder().userId(userId).username("github_test").role(Role.USER).build();
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    @DisplayName("미인증 → 401")
    void unauthenticated() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repoUrls\":[\"https://github.com/o/r\"],\"mode\":\"NONSTOP\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("repo 4개 → 400(검증)")
    void too_many_repos() throws Exception {
        
        mockMvc.perform(post(URL).with(auth()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repoUrls\":[\"https://github.com/o/a\",\"https://github.com/o/b\","
                                + "\"https://github.com/o/c\",\"https://github.com/o/d\"]}"))
                .andExpect(status().isBadRequest());
        then(sqsMessageSender).should(org.mockito.Mockito.never()).send(any(), any());
    }

    @Test
    @DisplayName("정상 디스패치 → 200 + batchId + SQS 전송 + DB IN_PROGRESS")
    void dispatch_ok() throws Exception {
        
        given(tokenPreparer.resolvePlaintextWithTtlCheck(userId)).willReturn("tok");
        given(tokenPreparer.reencryptForLambda("tok")).willReturn("enc");
        GitHubDto.ApiRepo repo = mock(GitHubDto.ApiRepo.class);
        given(repo.getSize()).willReturn(100);
        given(gitHubApiClient.fetchRepo("tok", "o", "r")).willReturn(repo);

        mockMvc.perform(post(URL).with(auth()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repoUrls\":[\"https://github.com/o/r\"],\"mode\":\"NONSTOP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").isNotEmpty())
                .andExpect(jsonPath("$.analyses[0].status").value("IN_PROGRESS"));

        then(sqsMessageSender).should(times(1)).send(any(), any());
        then(batchProgressTracker).should().createBatch(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(1));
        org.assertj.core.api.Assertions.assertThat(projectAnalysisRepository.findAll())
                .hasSize(1)
                .allSatisfy(a -> org.assertj.core.api.Assertions.assertThat(a.getAnalysisFlag().name())
                        .isEqualTo("IN_PROGRESS"));
    }
}
