package com.capstone.passfolio.system.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link SystemMonitoringController}. Notes:
 * <ul>
 *   <li>{@code @WebMvcTest} is used so the test runs without DB / Redis,
 *       per Backend/CLAUDE.md DevOps principle "External dependencies must
 *       be mockable".</li>
 *   <li>{@code PassfolioApplication} declares {@code @EnableJpaAuditing},
 *       which eagerly wires {@code jpaMappingContext} even in this slice.
 *       A {@link MockitoBean} for {@link JpaMetamodelMappingContext} satisfies
 *       that dependency without standing up JPA infra.</li>
 *   <li>{@code addFilters = false} disables Spring Security's default OAuth2
 *       client filter chain (the project uses
 *       {@code spring-boot-starter-oauth2-client}, which auto-configures a
 *       redirect to {@code /oauth2/authorization/github} for unauthenticated
 *       requests). The {@code /ping} endpoint is intentionally unauthenticated
 *       per the user story ("uptime monitor … reachable"), so bypassing the
 *       filter chain mirrors the production allow-list intent.</li>
 * </ul>
 */
@WebMvcTest(SystemMonitoringController.class)
@AutoConfigureMockMvc(addFilters = false)
class SystemMonitoringControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void shouldReturnPong() throws Exception {
        mockMvc.perform(get("/api/v1/system/monitor/ping"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith(MediaType.TEXT_PLAIN_VALUE)))
                .andExpect(content().string("pong"));
    }
}
