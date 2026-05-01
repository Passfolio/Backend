package com.capstone.passfolio.system.controller.e2e;

import com.capstone.passfolio.system.controller.SystemMonitoringController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box E2E test for {@code GET /api/v1/system/monitor/ping}.
 *
 * <p>This test boots a real Spring Boot web context on a random port and exercises
 * the endpoint over real HTTP via {@link TestRestTemplate}. It verifies the three
 * observable contract bits required by the backlog acceptance signal:
 * <ol>
 *   <li>HTTP status 200</li>
 *   <li>Content-Type starts with {@code text/plain}</li>
 *   <li>Body equals the literal 4-byte string {@code pong}</li>
 * </ol>
 *
 * <p><b>Why a custom minimal config instead of {@code @SpringBootTest} on
 * {@code PassfolioApplication}?</b><br>
 * The production {@code PassfolioApplication} pulls in Postgres/Flyway, Redis,
 * Mail, OAuth2 client, JPA, AWS SDK, and ~25 required environment variables.
 * Booting it for an E2E ping check would require Testcontainers (Postgres + Redis)
 * plus stub credentials for OAuth2/HMAC/GitHub/Mail/AWS — multi-minute startup
 * for a 4-byte response. Instead, this test stands up a focused web context with
 * {@link SystemMonitoringController} as the only application component and an
 * <i>allow-list</i> of autoconfigurations (web/MVC/Jackson/error handling only)
 * via {@code @ImportAutoConfiguration} — no JPA / Redis / Mail / Security /
 * Batch / AWS infra is loaded. The controller's response path is still fully
 * exercised end-to-end through Tomcat, Spring's {@code DispatcherServlet}, and
 * the {@code MessageConverter} chain — exactly the layers a {@code MockMvc}
 * slice does <i>not</i> exercise.
 *
 * <p><b>Known gap (out of scope per the dev entry):</b> production
 * {@code SecurityConfig} wiring is not exercised here. The backlog's design
 * Decision and the dev entry both note that {@code /api/v1/system/**} is
 * permitAll'd via {@code RequestMatcherHolder} in production; verifying that
 * allow-list end-to-end would require booting the full security stack (and
 * therefore the full app, including Postgres/Redis/OAuth2 secrets).
 */
@SpringBootTest(
        classes = PingE2ETest.MinimalPingApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.banner-mode=off",
                "spring.profiles.active="
        }
)
class PingE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void pingEndpoint_returnsPongOverRealHttp() {
        String url = "http://localhost:" + port + "/api/v1/system/monitor/ping";

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString())
                .startsWith(MediaType.TEXT_PLAIN_VALUE);
        assertThat(response.getBody()).isEqualTo("pong");
    }

    /**
     * Minimal Spring Boot application for the E2E test. Component-scans only the
     * controller under test and disables every autoconfiguration that requires
     * external infrastructure (DB / cache / mail / security / batch). The Web /
     * Tomcat / Jackson autoconfigurations are still active, so requests really
     * traverse the servlet container.
     */
    @Configuration
    @ImportAutoConfiguration({
            PropertyPlaceholderAutoConfiguration.class,
            ServletWebServerFactoryAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class,
            JacksonAutoConfiguration.class,
            ErrorMvcAutoConfiguration.class
    })
    @ComponentScan(
            basePackageClasses = SystemMonitoringController.class,
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = SystemMonitoringController.class
            )
    )
    static class MinimalPingApp {
    }
}
