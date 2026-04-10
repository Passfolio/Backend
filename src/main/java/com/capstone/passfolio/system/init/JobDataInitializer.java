package com.capstone.passfolio.system.init;

import com.capstone.passfolio.domain.spec.entity.Job;
import com.capstone.passfolio.domain.spec.entity.enums.JobTag;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class JobDataInitializer implements ApplicationRunner {

    private static final int BATCH_SIZE = 50;

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {

        final String resourcePath = "data/job_tables.json";
        long startedAtMs = System.currentTimeMillis();

        log.info(
                "[JobDataInitializer] Initializing started: resource={}, batchSize={}",
                resourcePath,
                BATCH_SIZE);

        InputStream is = new ClassPathResource(resourcePath).getInputStream();
        JsonNode root = objectMapper.readTree(is);

        List<Job> buffer = new ArrayList<>(BATCH_SIZE);
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();

        int totalProcessed = 0;
        int batchesFlushed = 0;

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JobTag tag = mapTag(entry.getKey());

            JsonNode jobArray = entry.getValue();
            if (jobArray == null || !jobArray.isArray()) {
                continue;
            }

            for (JsonNode node : jobArray) {
                Job job = mapToEntity(node, tag);
                if (job == null) {
                    continue;
                }

                buffer.add(job);
                if (buffer.size() == BATCH_SIZE) {
                    batchInsert(buffer);
                    int flushed = buffer.size();
                    buffer.clear();
                    totalProcessed += flushed;
                    batchesFlushed++;
                    log.info(
                            "[JobDataInitializer] Initializing progress: totalProcessed={}, batchesFlushed={}, lastBatchSize={}",
                            totalProcessed,
                            batchesFlushed,
                            flushed);
                }
            }
        }

        if (!buffer.isEmpty()) {
            batchInsert(buffer);
            int flushed = buffer.size();
            buffer.clear();
            totalProcessed += flushed;
            batchesFlushed++;
            log.info(
                    "[JobDataInitializer] Initializing progress: totalProcessed={}, batchesFlushed={}, lastBatchSize={} (final partial batch)",
                    totalProcessed,
                    batchesFlushed,
                    flushed);
        }

        long elapsedMs = System.currentTimeMillis() - startedAtMs;
        log.info(
                "🟢[JobDataInitializer] Initializing completion: totalProcessed={}, batchesFlushed={}, elapsedMs={}",
                totalProcessed,
                batchesFlushed,
                elapsedMs);
    }

    private Job mapToEntity(JsonNode node, JobTag tag) {
        JsonNode codeNode = node.get("code");
        JsonNode nameNode = node.get("name");

        if (codeNode == null || nameNode == null) {
            return null;
        }

        String keyword = nameNode.asText().trim();
        if (keyword.isBlank()) {
            return null;
        }

        return Job.builder()
                .jobCode(codeNode.asInt())
                .jobKeyword(keyword)
                .jobTag(tag)
                .build();
    }

    private JobTag mapTag(String rawTag) {
        return switch (rawTag) {
            case "직무" -> JobTag.ROLE;
            case "전문분야" -> JobTag.MAJOR;
            case "기술스택" -> JobTag.SKILL;
            default -> throw new IllegalStateException("Unknown job tag: " + rawTag);
        };
    }

    private void batchInsert(List<Job> jobs) {
        String sql = """
            INSERT INTO job (job_code, job_keyword, job_tag)
            VALUES (?, ?, ?)
            ON CONFLICT DO NOTHING
            """;

        jdbcTemplate.batchUpdate(sql, jobs, jobs.size(),
                (ps, entity) -> {
                    ps.setInt(1, entity.getJobCode());
                    ps.setString(2, entity.getJobKeyword());
                    ps.setString(3, entity.getJobTag().name());
                });
    }
}
