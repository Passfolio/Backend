package com.capstone.passfolio.system.config.jackson;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter KST_DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'+09:00'");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            // LocalDateTime 직렬화: KST로 가정하고 +09:00을 붙임 (Safari/Chrome 모두 인식)
            builder.serializerByType(
                    LocalDateTime.class,
                    new LocalDateTimeSerializer(KST_DATETIME_FORMATTER)
            );

            // LocalDateTime 역직렬화: +09:00이 붙은 형식도 처리 가능하도록 커스텀 포맷터 사용
            // +09:00은 리터럴로 처리하고 무시 (LocalDateTime은 타임존 정보 없음)
            builder.deserializerByType(
                    LocalDateTime.class,
                    new LocalDateTimeDeserializer(
                            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSSSSS]['+09:00'][Z]")
                    )
            );

            // 날짜를 타임스탬프가 아닌 문자열로 직렬화
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }
}


