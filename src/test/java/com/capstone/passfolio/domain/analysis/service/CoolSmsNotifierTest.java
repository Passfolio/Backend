package com.capstone.passfolio.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CoolSmsNotifierTest {

    @Mock private DefaultMessageService messageService;
    @Mock private BatchPhoneStore phoneStore;

    @InjectMocks private CoolSmsNotifier notifier;

    private void enabled(boolean v) {
        ReflectionTestUtils.setField(notifier, "smsEnabled", v);
        ReflectionTestUtils.setField(notifier, "from", "01000000000");
        // 맨 뒤 값(운영 도메인)을 링크 base로 쓰는지 검증하려고 첫 값은 localhost로 둠
        ReflectionTestUtils.setField(notifier, "frontBaseUrlConfig", "http://localhost:5173,https://passfolio.com");
    }

    @Test
    @DisplayName("phone 없음 → 발송 안 함")
    void no_phone_skips() {
        enabled(true);
        given(phoneStore.read("b1")).willReturn(null);

        notifier.notifyBatchCompleted(7L, "b1", 2, true);

        then(messageService).should(never()).sendOne(any(SingleMessageSendingRequest.class));
    }

    @Test
    @DisplayName("SMS 비활성 → 발송 안 함(로그만)")
    void disabled_skips_send() {
        enabled(false);
        given(phoneStore.read("b1")).willReturn("01012345678");

        notifier.notifyBatchCompleted(7L, "b1", 2, true);

        then(messageService).should(never()).sendOne(any(SingleMessageSendingRequest.class));
    }

    @Test
    @DisplayName("활성 + phone 있음 → sendOne 호출, 본문에 요약 문구 + 결과 보기 링크 포함")
    void enabled_with_phone_sends_with_link() {
        enabled(true);
        given(phoneStore.read("b1")).willReturn("01012345678");

        notifier.notifyBatchCompleted(7L, "b1", 2, true);

        ArgumentCaptor<SingleMessageSendingRequest> captor =
                ArgumentCaptor.forClass(SingleMessageSendingRequest.class);
        then(messageService).should().sendOne(captor.capture());
        String text = captor.getValue().getMessage().getText();
        assertThat(text).contains("프로젝트 분석 2건이 모두 완료되었습니다.");
        assertThat(text).contains("결과 보기: https://passfolio.com/analysis/b1");
    }

    @Test
    @DisplayName("sendOne 실패 → 예외 전파 안 함(best-effort)")
    void send_failure_is_best_effort() {
        enabled(true);
        given(phoneStore.read("b1")).willReturn("01012345678");
        willThrow(new RuntimeException("solapi down"))
                .given(messageService).sendOne(any(SingleMessageSendingRequest.class));

        assertThatCode(() -> notifier.notifyBatchCompleted(7L, "b1", 2, false))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("번호 정규화: E.164(+82)·하이픈을 국내 로컬 형식으로 변환")
    void normalize_converts_e164_and_hyphens() {
        assertThat(CoolSmsNotifier.normalize("+821097261322")).isEqualTo("01097261322");
        assertThat(CoolSmsNotifier.normalize("821097261322")).isEqualTo("01097261322");
        assertThat(CoolSmsNotifier.normalize("010-9726-1322")).isEqualTo("01097261322");
        assertThat(CoolSmsNotifier.normalize("01097261322")).isEqualTo("01097261322");
        assertThat(CoolSmsNotifier.normalize(null)).isNull();
    }
}
