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
import java.util.List;

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
        final String universityResourcePath = "data/universities.json";
        final String departmentResourcePath = "data/university_department.json";
        long startedAtMs = System.currentTimeMillis();

        log.info(
                "[UniversityDataInitializer] Initializing started: universitiesResource={}, departmentsResource={}, universityBatch={}, departmentBatch={}",
                universityResourcePath,
                departmentResourcePath,
                UNIVERSITY_BATCH_SIZE,
                DEPARTMENT_BATCH_SIZE);

        if (alreadyInitialized()) {
            log.info("[UniversityDataInitializer] Skip initialization: data already exists in university and university_department tables");
            return;
        }

        int totalUniversities = insertAllUniversities(new ClassPathResource(universityResourcePath));
        int totalDepartments = insertAllDepartments(new ClassPathResource(departmentResourcePath));
        syncUniversityDepartmentIdentitySequence();

        long elapsedMs = System.currentTimeMillis() - startedAtMs;
        log.info(
                "🟢[UniversityDataInitializer] Initializing completion: universitiesProcessed={}, departmentRowsProcessed={}, elapsedMs={}",
                totalUniversities,
                totalDepartments,
                elapsedMs);
    }

    private boolean alreadyInitialized() {
        Long universityCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM university", Long.class);
        Long departmentCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM university_department", Long.class);
        return universityCount != null && universityCount > 0
                && departmentCount != null && departmentCount > 0;
    }

    private int insertAllUniversities(ClassPathResource resource) throws Exception {
        List<University> buffer = new ArrayList<>(UNIVERSITY_BATCH_SIZE);
        int total = 0;
        int batches = 0;

        try (InputStream is = resource.getInputStream();
                JsonParser parser = objectMapper.getFactory().createParser(is)) {

            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IllegalStateException("Invalid JSON format");
            }

            while (parser.nextToken() == JsonToken.START_OBJECT) {
                JsonNode node = objectMapper.readTree(parser);
                University row = parseUniversityRow(node);

                if (row == null) { continue; }

                buffer.add(row);
                if (buffer.size() >= UNIVERSITY_BATCH_SIZE) {
                    batchInsertUniversities(buffer);
                    total += buffer.size();
                    buffer.clear();
                    batches++;
                    log.info(
                            "[UniversityDataInitializer] University insert progress: totalRows={}, batchesFlushed={}",
                            total,
                            batches);
                }
            }
        }

        if (!buffer.isEmpty()) {
            batchInsertUniversities(buffer);
            total += buffer.size();
            batches++;
            log.info(
                    "[UniversityDataInitializer] University insert progress: totalRows={}, batchesFlushed={} (final)",
                    total,
                    batches);
        }

        return total;
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
                DepartmentInsertRow row = parseDepartmentRow(node);
                if (row == null) {
                    continue;
                }

                buffer.add(row);

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

    private University parseUniversityRow(JsonNode node) {
        String id = normalizeText(getText(node, "univ_uuid"));
        String name = normalizeText(getText(node, "univ_name"));
        String type = normalizeText(getText(node, "univ_type"));
        String region = normalizeText(getText(node, "region"));
        if (id.isBlank() || name.isBlank() || type.isBlank() || region.isBlank()) {
            return null;
        }

        return University.builder()
                .id(id)
                .name(name)
                .type(type)
                .region(region)
                .build();
    }

    private DepartmentInsertRow parseDepartmentRow(JsonNode node) {
        long id = getLong(node, "department_id");
        String universityId = normalizeText(getText(node, "univ_uuid"));
        String department = normalizeText(getText(node, "department"));
        String degree = normalizeText(getText(node, "degree"));
        String duration = normalizeText(getText(node, "duration"));

        if (id <= 0 || universityId.isBlank() || department.isBlank() || degree.isBlank() || duration.isBlank()) {
            return null;
        }

        return new DepartmentInsertRow(id, universityId, department, degree, duration);
    }

    private void batchInsertUniversities(List<University> list) {
        String sql = """
                INSERT INTO university (id, name, type, region)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """;

        jdbcTemplate.batchUpdate(sql, list, list.size(),
                (ps, entity) -> {
                    ps.setString(1, entity.getId());
                    ps.setString(2, entity.getName());
                    ps.setString(3, entity.getType());
                    ps.setString(4, entity.getRegion());
                });
    }

    private void batchInsertDepartments(List<DepartmentInsertRow> rows) {
        String sql = """
                INSERT INTO university_department (id, university_id, department, degree, duration)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """;

        jdbcTemplate.batchUpdate(sql, rows, rows.size(),
                (ps, row) -> {
                    ps.setLong(1, row.id());
                    ps.setString(2, row.universityId());
                    ps.setString(3, row.department());
                    ps.setString(4, row.degree());
                    ps.setString(5, row.duration());
                });
    }

    private String getText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null ? value.asText().trim() : "";
    }

    private long getLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return 0;
        }
        return value.asLong(0);
    }

    private String normalizeText(String raw) {
        return raw
                .trim()
                .replaceAll("\\s+", " ");
    }

    private void syncUniversityDepartmentIdentitySequence() {
        String sql = """
                SELECT setval(
                    pg_get_serial_sequence('university_department', 'id'),
                    COALESCE((SELECT MAX(id) FROM university_department), 1),
                    true
                )
                """;
        jdbcTemplate.queryForObject(sql, Long.class);
    }

    private record DepartmentInsertRow(
            long id,
            String universityId,
            String department,
            String degree,
            String duration
    ) {}
}
