package com.capstone.passfolio.system.config.sms;

import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * CoolSMS(솔라피) 메시지 서비스 빈(배치 완료 SMS). AWS SNS를 대체.
 * apiKey/apiSecret으로 서명을 SDK가 처리한다. 키 미설정(개발)이어도 빈은 생성되며,
 * 실제 발송 차단은 CoolSmsNotifier의 sms.coolsms.enabled 플래그가 담당한다.
 */
@Configuration
public class CoolSmsConfig {

    @Value("${sms.coolsms.api-key:}")
    private String apiKey;

    @Value("${sms.coolsms.api-secret:}")
    private String apiSecret;

    @Value("${sms.coolsms.base-url:https://api.solapi.com}")
    private String baseUrl;

    @Bean
    public DefaultMessageService coolSmsMessageService() {
        return NurigoApp.INSTANCE.initialize(apiKey, apiSecret, baseUrl);
    }
}
