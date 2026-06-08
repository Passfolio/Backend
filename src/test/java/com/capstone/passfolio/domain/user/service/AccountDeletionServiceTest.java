package com.capstone.passfolio.domain.user.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.capstone.passfolio.domain.ai.repository.AiJobRepository;
import com.capstone.passfolio.domain.analysis.repository.ProjectAnalysisRepository;
import com.capstone.passfolio.domain.analysis.repository.RepoAvailabilityRepository;
import com.capstone.passfolio.domain.article.service.ArticleService;
import com.capstone.passfolio.domain.file.service.FileService;
import com.capstone.passfolio.domain.github.client.GitHubOAuthRevokeClient;
import com.capstone.passfolio.domain.github.repository.GitHubTokenRedisRepository;
import com.capstone.passfolio.domain.spec.repository.DevSpecCareerRepository;
import com.capstone.passfolio.domain.spec.repository.DevSpecEducationRepository;
import com.capstone.passfolio.domain.spec.repository.DevSpecRepository;
import com.capstone.passfolio.domain.user.dto.UserSoftDeleteDto;
import com.capstone.passfolio.domain.user.entity.User;
import com.capstone.passfolio.domain.user.entity.enums.Role;
import com.capstone.passfolio.domain.user.repository.UserRepository;
import com.capstone.passfolio.system.config.encryption.AesEncryptor;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountDeletionService — role 분기(USER soft / ADMIN hard) + 자식 정리 순서")
class AccountDeletionServiceTest {

    @Mock private GitHubTokenRedisRepository githubTokenRedisRepository;
    @Mock private GitHubOAuthRevokeClient githubOAuthRevokeClient;
    @Mock private AesEncryptor aesEncryptor;
    @Mock private ArticleService articleService;
    @Mock private FileService fileService;
    @Mock private ProjectAnalysisRepository projectAnalysisRepository;
    @Mock private RepoAvailabilityRepository repoAvailabilityRepository;
    @Mock private AiJobRepository aiJobRepository;
    @Mock private DevSpecEducationRepository devSpecEducationRepository;
    @Mock private DevSpecCareerRepository devSpecCareerRepository;
    @Mock private DevSpecRepository devSpecRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private AccountDeletionService service;

    @Test
    @DisplayName("USER → softDelete 호출 + deleteByUserId 미호출, 모든 자식 정리")
    void user_softDelete_and_children_cleaned() {
        User user = mock(User.class);
        given(user.getId()).willReturn(1L);
        given(user.getRole()).willReturn(Role.USER);
        given(githubTokenRedisRepository.getAccessToken(1L)).willReturn(Optional.empty());

        service.purge(user);

        then(user).should().softDelete(any(UserSoftDeleteDto.class));
        then(userRepository).should(never()).deleteByUserId(any());
        then(articleService).should().deleteAllByWriter(1L);
        then(fileService).should().deleteAllByOwner(1L);
        then(projectAnalysisRepository).should().deleteAllByUserId(1L);
        then(repoAvailabilityRepository).should().deleteAllByUserId(1L);
        then(devSpecEducationRepository).should().deleteAllByDevSpecId(1L);
        then(devSpecCareerRepository).should().deleteAllByDevSpecId(1L);
        then(devSpecRepository).should().deleteByUserId(1L);
        then(aiJobRepository).should().deleteAllByUserId(1L);
    }

    @Test
    @DisplayName("ADMIN → deleteByUserId(Hard) 호출 + softDelete 미호출")
    void admin_hardDelete() {
        User user = mock(User.class);
        given(user.getId()).willReturn(2L);
        given(user.getRole()).willReturn(Role.ADMIN);
        given(githubTokenRedisRepository.getAccessToken(2L)).willReturn(Optional.empty());

        service.purge(user);

        then(userRepository).should().deleteByUserId(2L);
        then(user).should(never()).softDelete(any());
    }

    @Test
    @DisplayName("삭제 순서 — 자식(article·file·analysis·repo·devSpec·ai) 후 user")
    void order_children_before_user() {
        User user = mock(User.class);
        given(user.getId()).willReturn(3L);
        given(user.getRole()).willReturn(Role.ADMIN);
        given(githubTokenRedisRepository.getAccessToken(3L)).willReturn(Optional.empty());

        service.purge(user);

        InOrder o = inOrder(articleService, fileService, projectAnalysisRepository,
                repoAvailabilityRepository, devSpecEducationRepository, devSpecCareerRepository,
                devSpecRepository, aiJobRepository, userRepository);
        o.verify(articleService).deleteAllByWriter(3L);
        o.verify(fileService).deleteAllByOwner(3L);
        o.verify(projectAnalysisRepository).deleteAllByUserId(3L);
        o.verify(repoAvailabilityRepository).deleteAllByUserId(3L);
        o.verify(devSpecEducationRepository).deleteAllByDevSpecId(3L);
        o.verify(devSpecCareerRepository).deleteAllByDevSpecId(3L);
        o.verify(devSpecRepository).deleteByUserId(3L);
        o.verify(aiJobRepository).deleteAllByUserId(3L);
        o.verify(userRepository).deleteByUserId(3L);
    }

    @Test
    @DisplayName("GitHub 토큰 있으면 grant revoke + Redis 토큰 제거")
    void github_grant_revoked_when_token_present() {
        User user = mock(User.class);
        given(user.getId()).willReturn(4L);
        given(user.getRole()).willReturn(Role.USER);
        given(githubTokenRedisRepository.getAccessToken(4L)).willReturn(Optional.of("enc"));
        given(aesEncryptor.decrypt("enc")).willReturn("plain");

        service.purge(user);

        then(githubOAuthRevokeClient).should().revokeGrant("plain");
        then(githubTokenRedisRepository).should().deleteAccessToken(4L);
    }
}
