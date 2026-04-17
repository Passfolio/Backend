package com.capstone.passfolio.system.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostgresInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        long startedAtMs = System.currentTimeMillis();
        log.info("[PostgresInitializer] pg_trgm and index initialization started");

        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");

        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_university_name_trgm
                ON university
                USING GIN (name gin_trgm_ops)
                """);

        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_university_department_name_trgm
                ON university_department
                USING GIN (department gin_trgm_ops)
                """);

        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_university_department_university_id
                ON university_department (university_id)
                """);

        long elapsedMs = System.currentTimeMillis() - startedAtMs;
        log.info("[PostgresInitializer] pg_trgm and index initialization completed: elapsedMs={}", elapsedMs);
    }
}
