package com.capstone.passfolio.system.init;

import com.capstone.passfolio.domain.spec.dto.DevSpecDto;
import com.capstone.passfolio.domain.spec.entity.University;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class UniversityDataInitializer implements ApplicationRunner {

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    private static final int BATCH_SIZE = 1000;

    @Override
    public void run(ApplicationArguments args) throws Exception {

        InputStream is = new ClassPathResource("data/universities.json").getInputStream();
        JsonParser parser = objectMapper.getFactory().createParser(is);

        List<University> buffer = new ArrayList<>(BATCH_SIZE);

        if (parser.nextToken() != JsonToken.START_ARRAY) {
            throw new IllegalStateException("Invalid JSON format");
        }

        while (parser.nextToken() == JsonToken.START_OBJECT) {

            JsonNode node = objectMapper.readTree(parser);

            DevSpecDto.UniversityInfo dto = mapToDto(node);

            University entity = dto.toEntity();
            buffer.add(entity);

            if (buffer.size() == BATCH_SIZE) {
                batchInsert(buffer);
                buffer.clear();
            }
        }

        if (!buffer.isEmpty()) {
            batchInsert(buffer);
        }
    }

    /**
     * JSON → DTO 매핑 (핵심)
     */
    private DevSpecDto.UniversityInfo mapToDto(JsonNode node) {

        String name = normalizeName(getText(node, "name"));

        String country = getText(node, "country");
        String countryCode = getText(node, "alpha_two_code");

        String domain = extractDomain(node);

        return DevSpecDto.UniversityInfo.builder()
                .name(name)
                .domain(domain)
                .countryCode(countryCode)
                .country(country)
                .build();
    }

    /**
     * Batch Insert (부분 Skip)
     */
    private void batchInsert(List<University> list) {

        String sql = """
        INSERT INTO university (name, domain, country_code, country)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (domain) DO NOTHING
    """;

        jdbcTemplate.batchUpdate(sql, list, list.size(),
                (ps, entity) -> {
                    ps.setString(1, entity.getName());
                    ps.setString(2, entity.getDomain());
                    ps.setString(3, entity.getCountryCode());
                    ps.setString(4, entity.getCountry());
                });
    }

    /**
     * domain 추출 (JSON 배열 대응)
     */
    private String extractDomain(JsonNode node) {

        JsonNode domainsNode = node.get("domains");

        if (domainsNode != null && domainsNode.isArray() && domainsNode.size() > 0) {

            int lastIndex = domainsNode.size() - 1;
            return normalizeDomain(domainsNode.get(lastIndex).asText());
        }

        return "";
    }

    private String getText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null ? value.asText().trim() : "";
    }

    private String normalizeName(String raw) {
        return raw
                .trim()
                .replaceAll("\\s+", " ");
    }

    private String normalizeDomain(String raw) {
        return raw
                .replace("http://", "")
                .replace("https://", "")
                .replace("www.", "")
                .trim()
                .toLowerCase();
    }
}