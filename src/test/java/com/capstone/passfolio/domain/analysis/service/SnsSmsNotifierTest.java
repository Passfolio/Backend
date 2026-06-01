package com.capstone.passfolio.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

@ExtendWith(MockitoExtension.class)
class SnsSmsNotifierTest {

    @Mock private SnsClient snsClient;
    @Mock private BatchPhoneStore phoneStore;

    @InjectMocks private SnsSmsNotifier notifier;

    private void enabled(boolean v) {
        ReflectionTestUtils.setField(notifier, "snsEnabled", v);
    }

    @Test
    @DisplayName("phone 없음 → 발송 안 함")
    void no_phone_skips() {
        enabled(true);
        given(phoneStore.read("b1")).willReturn(null);

        notifier.notifyBatchCompleted(7L, "b1", 2, true);

        then(snsClient).should(never()).publish(any(PublishRequest.class));
    }

    @Test
    @DisplayName("SNS 비활성 → 발송 안 함(로그만)")
    void disabled_skips_publish() {
        enabled(false);
        given(phoneStore.read("b1")).willReturn("+821012345678");

        notifier.notifyBatchCompleted(7L, "b1", 2, true);

        then(snsClient).should(never()).publish(any(PublishRequest.class));
    }

    @Test
    @DisplayName("활성 + phone 있음 → SNS publish")
    void enabled_with_phone_publishes() {
        enabled(true);
        given(phoneStore.read("b1")).willReturn("+821012345678");

        notifier.notifyBatchCompleted(7L, "b1", 2, true);

        then(snsClient).should().publish(any(PublishRequest.class));
    }

    @Test
    @DisplayName("publish 실패 → 예외 전파 안 함(best-effort)")
    void publish_failure_is_best_effort() {
        enabled(true);
        given(phoneStore.read("b1")).willReturn("+821012345678");
        willThrow(new RuntimeException("sns down")).given(snsClient).publish(any(PublishRequest.class));

        assertThatCode(() -> notifier.notifyBatchCompleted(7L, "b1", 2, false))
                .doesNotThrowAnyException();
    }
}
