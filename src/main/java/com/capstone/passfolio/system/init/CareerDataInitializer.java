package com.capstone.passfolio.system.init;

import com.capstone.passfolio.common.util.UuidGenerator;
import com.capstone.passfolio.domain.spec.entity.Career;
import com.capstone.passfolio.domain.spec.entity.enums.CareerTag;
import com.capstone.passfolio.domain.spec.entity.enums.ThirdParty;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CareerDataInitializer implements ApplicationRunner {

    private static final int BATCH_SIZE = 50;

    /** `career_tables.json` 출처(사람인 표 기준) */
    private static final ThirdParty CAREER_TABLE_SOURCE = ThirdParty.SARAMIN;

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {

        final String resourcePath = "data/career_tables.json";
        long startedAtMs = System.currentTimeMillis();

        log.info(
                "[CareerDataInitializer] Initializing started: resource={}, batchSize={}, thirdParty={}",
                resourcePath,
                BATCH_SIZE,
                CAREER_TABLE_SOURCE);

        InputStream is = new ClassPathResource(resourcePath).getInputStream();
        JsonNode root = objectMapper.readTree(is);

        List<Career> careerBuffer = new ArrayList<>(BATCH_SIZE);
        List<CareerThirdPartyRow> thirdPartyBuffer = new ArrayList<>(BATCH_SIZE);
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();

        int totalCareersFlushed = 0;
        int batchesFlushed = 0;

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            CareerTag tag = mapTag(entry.getKey());

            JsonNode careerArray = entry.getValue();
            if (careerArray == null || !careerArray.isArray()) {
                continue;
            }

            for (JsonNode node : careerArray) {
                Career career = mapToCareer(node, tag);
                if (career == null) {
                    continue;
                }

                String externalCode = externalCareerCode(node.get("code"));
                if (externalCode == null || externalCode.isBlank()) {
                    continue;
                }

                careerBuffer.add(career);
                thirdPartyBuffer.add(new CareerThirdPartyRow(externalCode, CAREER_TABLE_SOURCE.name(), career.getId()));

                if (careerBuffer.size() == BATCH_SIZE) {
                    flushBatch(careerBuffer, thirdPartyBuffer);
                    int flushed = BATCH_SIZE;
                    totalCareersFlushed += flushed;
                    batchesFlushed++;
                    log.info(
                            "[CareerDataInitializer] Initializing progress: totalCareersFlushed={}, batchesFlushed={}, lastBatchSize={}",
                            totalCareersFlushed,
                            batchesFlushed,
                            flushed);
                }
            }
        }

        if (!careerBuffer.isEmpty()) {
            int flushed = careerBuffer.size();
            flushBatch(careerBuffer, thirdPartyBuffer);
            totalCareersFlushed += flushed;
            batchesFlushed++;
            log.info(
                    "[CareerDataInitializer] Initializing progress: totalCareersFlushed={}, batchesFlushed={}, lastBatchSize={} (final partial batch)",
                    totalCareersFlushed,
                    batchesFlushed,
                    flushed);
        }

        long elapsedMs = System.currentTimeMillis() - startedAtMs;
        log.info(
                "🟢[CareerDataInitializer] Initializing completion: totalCareersFlushed={}, batchesFlushed={}, elapsedMs={}",
                totalCareersFlushed,
                batchesFlushed,
                elapsedMs);
    }

    private void flushBatch(List<Career> careerBuffer, List<CareerThirdPartyRow> thirdPartyBuffer) {
        batchInsertCareers(careerBuffer);
        batchInsertCareerThirdParty(thirdPartyBuffer);
        careerBuffer.clear();
        thirdPartyBuffer.clear();
    }

    private Career mapToCareer(JsonNode node, CareerTag tag) {
        JsonNode nameNode = node.get("name");
        if (nameNode == null) {
            return null;
        }

        String keyword = normalizeCareerKeyword(nameNode.asText());
        if (keyword.isBlank()) {
            return null;
        }

        String canonicalKey = tag.name() + ":" + keyword;
        UUID uuid = UuidGenerator.generate(canonicalKey);

        return Career.builder()
                .id(uuid.toString())
                .careerKeyword(keyword)
                .careerTag(tag)
                .build();
    }

    private static String externalCareerCode(JsonNode codeNode) {
        if (codeNode == null || codeNode.isNull()) {
            return null;
        }
        if (codeNode.isNumber()) {
            return String.valueOf(codeNode.asLong());
        }
        return codeNode.asText().trim();
    }

    private static String normalizeCareerKeyword(String raw) {
        return raw
                .trim()
                .replaceAll("\\s+", " ");
    }

    private CareerTag mapTag(String rawTag) {
        return switch (rawTag) {
            case "직무" -> CareerTag.ROLE;
            case "전문분야" -> CareerTag.MAJOR;
            case "기술스택" -> CareerTag.SKILL;
            default -> throw new IllegalStateException("Unknown career tag: " + rawTag);
        };
    }

    private void batchInsertCareers(List<Career> careers) {
        String sql = """
                INSERT INTO career (id, career_keyword, career_tag)
                VALUES (?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """;

        jdbcTemplate.batchUpdate(sql, careers, careers.size(),
                (ps, entity) -> {
                    ps.setString(1, entity.getId());
                    ps.setString(2, entity.getCareerKeyword());
                    ps.setString(3, entity.getCareerTag().name());
                });
    }

    private void batchInsertCareerThirdParty(List<CareerThirdPartyRow> rows) {
        String sql = """
                INSERT INTO career_third_party (career_code, third_party, career_id)
                VALUES (?, ?, ?)
                ON CONFLICT (career_code, third_party) DO NOTHING
                """;

        jdbcTemplate.batchUpdate(sql, rows, rows.size(),
                (ps, row) -> {
                    ps.setString(1, row.careerCode());
                    ps.setString(2, row.thirdParty());
                    ps.setString(3, row.careerId());
                });
    }

    private record CareerThirdPartyRow(String careerCode, String thirdParty, String careerId) {}
}
