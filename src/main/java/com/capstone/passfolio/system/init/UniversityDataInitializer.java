package com.capstone.passfolio.system.init;

import com.capstone.passfolio.domain.spec.dto.DevSpecDto;
import com.capstone.passfolio.domain.spec.entity.University;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class UniversityDataInitializer implements ApplicationRunner {

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    private static final int BATCH_SIZE = 5000;

    @Override
    public void run(ApplicationArguments args) throws Exception {

        final String resourcePath = "data/2025_university_department_data_processed.json";
        long startedAtMs = System.currentTimeMillis();

        log.info(
                "[UniversityDataInitializer] Initializing started: resource={}, batchSize={}",
                resourcePath,
                BATCH_SIZE);

        InputStream is = new ClassPathResource(resourcePath).getInputStream();
        JsonParser parser = objectMapper.getFactory().createParser(is);

        List<University> buffer = new ArrayList<>(BATCH_SIZE);

        if (parser.nextToken() != JsonToken.START_ARRAY) {
            throw new IllegalStateException("Invalid JSON format");
        }

        int totalProcessed = 0;
        int batchesFlushed = 0;

        while (parser.nextToken() == JsonToken.START_OBJECT) {

            JsonNode node = objectMapper.readTree(parser);

            DevSpecDto.UniversityInfo dto = mapToDto(node);

            University entity = dto.toEntity();
            buffer.add(entity);

            if (buffer.size() == BATCH_SIZE) {
                batchInsert(buffer);
                int flushed = buffer.size();
                buffer.clear();
                totalProcessed += flushed;
                batchesFlushed++;
                log.info(
                        "[UniversityDataInitializer] Initializing progress: totalProcessed={}, batchesFlushed={}, lastBatchSize={}",
                        totalProcessed,
                        batchesFlushed,
                        flushed);
            }
        }

        if (!buffer.isEmpty()) {
            batchInsert(buffer);
            int flushed = buffer.size();
            buffer.clear();
            totalProcessed += flushed;
            batchesFlushed++;
            log.info(
                    "[UniversityDataInitializer] Initializing progress: totalProcessed={}, batchesFlushed={}, lastBatchSize={} (final partial batch)",
                    totalProcessed,
                    batchesFlushed,
                    flushed);
        }

        long elapsedMs = System.currentTimeMillis() - startedAtMs;
        log.info(
                "🟢[UniversityDataInitializer] Initializing completion: totalProcessed={}, batchesFlushed={}, elapsedMs={}",
                totalProcessed,
                batchesFlushed,
                elapsedMs);
    }

    /**
     * JSON → DTO 매핑 (핵심)
     */
    private DevSpecDto.UniversityInfo mapToDto(JsonNode node) {

        String name = normalizeName(getText(node, "학교명"));
        String educationType = normalizeText(getText(node, "학제"));
        String campusType = normalizeText(getText(node, "본분교"));
        String region = normalizeText(getText(node, "시도"));
        String departmentName = normalizeText(getText(node, "학과명"));
        int educationLevelCode = getInt(node, "학력코드");
        String educationLevel = normalizeText(getText(node, "학력"));
        String pageUrl = normalizePageUrl(getText(node, "홈페이지"));

        return DevSpecDto.UniversityInfo.builder()
                .name(name)
                .educationType(educationType)
                .campusType(campusType)
                .region(region)
                .departmentName(departmentName)
                .educationLevelCode(educationLevelCode)
                .educationLevel(educationLevel)
                .pageUrl(pageUrl)
                .build();
    }

    /**
     * Batch Insert (부분 Skip)
     */
    private void batchInsert(List<University> list) {

        String sql = """
        INSERT INTO university (name, education_type, campus_type, region, department_name, education_level_code, education_level, page_url)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (page_url) DO NOTHING
    """;

        jdbcTemplate.batchUpdate(sql, list, list.size(),
                (ps, entity) -> {
                    ps.setString(1, entity.getName());
                    ps.setString(2, entity.getEducationType());
                    ps.setString(3, entity.getCampusType());
                    ps.setString(4, entity.getRegion());
                    ps.setString(5, entity.getDepartmentName());
                    ps.setInt(6, entity.getEducationLevelCode());
                    ps.setString(7, entity.getEducationLevel());
                    ps.setString(8, entity.getPageUrl());
                });
    }

    private String getText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null ? value.asText().trim() : "";
    }

    private int getInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return 0;
        }
        return value.asInt(0);
    }

    private String normalizeName(String raw) {
        return raw
                .trim()
                .replaceAll("\\s+", " ");
    }

    private String normalizeText(String raw) {
        return raw
                .trim()
                .replaceAll("\\s+", " ");
    }

    private String normalizePageUrl(String raw) {
        return raw == null ? "" : raw.trim();
    }
}