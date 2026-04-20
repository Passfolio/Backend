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

    private static final DateTimeFormatter UTC_DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            // LocalDateTime 직렬화: UTC 기준으로 Z를 붙여 일관된 응답 형식 유지
            builder.serializerByType(
                    LocalDateTime.class,
                    new LocalDateTimeSerializer(UTC_DATETIME_FORMATTER)
            );

            // LocalDateTime 역직렬화: UTC(Z) 입력 허용
            builder.deserializerByType(
                    LocalDateTime.class,
                    new LocalDateTimeDeserializer(
                            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSSSSS]['Z']")
                    )
            );

            // 날짜를 타임스탬프가 아닌 문자열로 직렬화
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }
}


