package com.capstone.passfolio.system.init;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class UniversityDataInitializer implements ApplicationRunner {

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    private static final int UNIVERSITY_BATCH_SIZE = 500;
    private static final int DEPARTMENT_BATCH_SIZE = 5000;

    @Override
    public void run(ApplicationArguments args) throws Exception {

        final String resourcePath = "data/2025_university_department_data_processed.json";
        long startedAtMs = System.currentTimeMillis();

        log.info(
                "[UniversityDataInitializer] Initializing started: resource={}, universityBatch={}, departmentBatch={}",
                resourcePath,
                UNIVERSITY_BATCH_SIZE,
                DEPARTMENT_BATCH_SIZE);

        ClassPathResource resource = new ClassPathResource(resourcePath);

        Map<String, University> uniqueUniversities = collectUniqueUniversities(resource);
        insertUniversitiesInBatches(uniqueUniversities);

        int totalDepartments = insertAllDepartments(resource);

        long elapsedMs = System.currentTimeMillis() - startedAtMs;
        log.info(
                "🟢[UniversityDataInitializer] Initializing completion: uniqueUniversities={}, departmentRowsProcessed={}, elapsedMs={}",
                uniqueUniversities.size(),
                totalDepartments,
                elapsedMs);
    }

    private Map<String, University> collectUniqueUniversities(ClassPathResource resource) throws Exception {
        Map<String, University> byId = new LinkedHashMap<>();

        try (InputStream is = resource.getInputStream();
                JsonParser parser = objectMapper.getFactory().createParser(is)) {

            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IllegalStateException("Invalid JSON format");
            }

            while (parser.nextToken() == JsonToken.START_OBJECT) {
                JsonNode node = objectMapper.readTree(parser);
                ParsedRow row = parseRow(node);
                if (row == null) {
                    continue;
                }

                String univId = University.deterministicId(
                        row.name(),
                        row.educationType(),
                        row.campusType(),
                        row.region());

                byId.putIfAbsent(
                        univId,
                        University.builder()
                                .id(univId)
                                .name(row.name())
                                .educationType(row.educationType())
                                .campusType(row.campusType())
                                .region(row.region())
                                .pageUrl(row.pageUrl())
                                .build());
            }
        }

        log.info("[UniversityDataInitializer] Pass1 done: uniqueUniversities={}", byId.size());
        return byId;
    }

    private void insertUniversitiesInBatches(Map<String, University> uniqueUniversities) {
        List<University> buffer = new ArrayList<>(UNIVERSITY_BATCH_SIZE);
        int batches = 0;
        for (University u : uniqueUniversities.values()) {
            buffer.add(u);
            if (buffer.size() >= UNIVERSITY_BATCH_SIZE) {
                batchInsertUniversities(buffer);
                buffer.clear();
                batches++;
                log.info("[UniversityDataInitializer] University insert progress: batchesFlushed={}", batches);
            }
        }
        if (!buffer.isEmpty()) {
            batchInsertUniversities(buffer);
            batches++;
            log.info("[UniversityDataInitializer] University insert progress: batchesFlushed={} (final)", batches);
        }
    }

    private int insertAllDepartments(ClassPathResource resource) throws Exception {
        List<DepartmentInsertRow> buffer = new ArrayList<>(DEPARTMENT_BATCH_SIZE);
        int total = 0;
        int batches = 0;

        try (InputStream is = resource.getInputStream();
                JsonParser parser = objectMapper.getFactory().createParser(is)) {

            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IllegalStateException("Invalid JSON format");
            }

            while (parser.nextToken() == JsonToken.START_OBJECT) {
                JsonNode node = objectMapper.readTree(parser);
                ParsedRow row = parseRow(node);
                if (row == null) {
                    continue;
                }

                String univId = University.deterministicId(
                        row.name(),
                        row.educationType(),
                        row.campusType(),
                        row.region());

                buffer.add(new DepartmentInsertRow(
                        univId,
                        row.departmentName(),
                        row.educationLevelCode(),
                        row.educationLevel()));

                if (buffer.size() >= DEPARTMENT_BATCH_SIZE) {
                    batchInsertDepartments(buffer);
                    total += buffer.size();
                    buffer.clear();
                    batches++;
                    log.info(
                            "[UniversityDataInitializer] Department insert progress: totalRows={}, batchesFlushed={}",
                            total,
                            batches);
                }
            }
        }

        if (!buffer.isEmpty()) {
            batchInsertDepartments(buffer);
            total += buffer.size();
            batches++;
            log.info(
                    "[UniversityDataInitializer] Department insert progress: totalRows={}, batchesFlushed={} (final)",
                    total,
                    batches);
        }

        return total;
    }

    private ParsedRow parseRow(JsonNode node) {
        String name = normalizeName(getText(node, "학교명"));
        if (name.isBlank()) {
            return null;
        }
        String educationType = normalizeText(getText(node, "학제"));
        String campusType = normalizeText(getText(node, "본분교"));
        String region = normalizeText(getText(node, "시도"));
        String departmentName = normalizeText(getText(node, "학과명"));
        if (departmentName.isBlank()) {
            return null;
        }
        int educationLevelCode = getInt(node, "학력코드");
        String educationLevel = normalizeText(getText(node, "학력"));
        String pageUrl = normalizePageUrl(getText(node, "홈페이지"));

        return new ParsedRow(name, educationType, campusType, region, departmentName, educationLevelCode, educationLevel, pageUrl);
    }

    private void batchInsertUniversities(List<University> list) {
        String sql = """
                INSERT INTO university (id, name, education_type, campus_type, region, page_url)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """;

        jdbcTemplate.batchUpdate(sql, list, list.size(),
                (ps, entity) -> {
                    ps.setString(1, entity.getId());
                    ps.setString(2, entity.getName());
                    ps.setString(3, entity.getEducationType());
                    ps.setString(4, entity.getCampusType());
                    ps.setString(5, entity.getRegion());
                    ps.setString(6, entity.getPageUrl());
                });
    }

    private void batchInsertDepartments(List<DepartmentInsertRow> rows) {
        String sql = """
                INSERT INTO university_department (university_id, department_name, education_level_code, education_level)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (university_id, department_name, education_level_code) DO NOTHING
                """;

        jdbcTemplate.batchUpdate(sql, rows, rows.size(),
                (ps, row) -> {
                    ps.setString(1, row.universityId());
                    ps.setString(2, row.departmentName());
                    ps.setInt(3, row.educationLevelCode());
                    ps.setString(4, row.educationLevel());
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

    private record ParsedRow(
            String name,
            String educationType,
            String campusType,
            String region,
            String departmentName,
            int educationLevelCode,
            String educationLevel,
            String pageUrl
    ) {}

    private record DepartmentInsertRow(
            String universityId,
            String departmentName,
            int educationLevelCode,
            String educationLevel
    ) {}
}
