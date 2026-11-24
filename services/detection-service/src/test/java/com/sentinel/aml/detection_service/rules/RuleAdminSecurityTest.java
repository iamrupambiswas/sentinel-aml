package com.sentinel.aml.detection_service.rules;

import com.sentinel.aml.common.security.SentinelSecurityConfig;
import com.sentinel.aml.detection_service.config.SecurityRoutes;
import com.sentinel.aml.detection_service.rules.RuleAdminDtos.RuleView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 6 route rules for detection-service, through the real shared filter chain. */
@WebMvcTest(RuleAdminController.class)
@Import({SentinelSecurityConfig.class, SecurityRoutes.class})
class RuleAdminSecurityTest {

    private static final String BODY = """
            {"weight": 40, "expectedVersion": 1, "reason": "tune"}""";

    @Autowired MockMvc mvc;
    @MockBean RuleAdminService ruleAdminService;
    @MockBean JwtDecoder jwtDecoder; // tokens are injected by jwt() below; no Keycloak needed

    private static RequestPostProcessor as(String user, String role) {
        return jwt().jwt(j -> j.subject(user)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void noTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/rules")).andExpect(status().isUnauthorized());
    }

    @Test
    void analystCanReadRules() throws Exception {
        when(ruleAdminService.list()).thenReturn(List.of());
        mvc.perform(get("/api/v1/rules").with(as("analyst1", "ANALYST"))).andExpect(status().isOk());
    }

    @Test
    void analystCannotChangeRules() throws Exception {
        mvc.perform(put("/api/v1/rules/CTR_THRESHOLD").with(as("analyst1", "ANALYST"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminChangeIsRecordedUnderTokenUsername() throws Exception {
        when(ruleAdminService.update(eq("CTR_THRESHOLD"), any(), eq("admin1"))).thenReturn(new RuleView(
                "CTR_THRESHOLD", "CTR", "d", BigDecimal.ONE, 0L, BigDecimal.TEN, true, Map.of(), 2, 1, Set.of()));

        mvc.perform(put("/api/v1/rules/CTR_THRESHOLD").with(as("admin1", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
        verify(ruleAdminService).update(eq("CTR_THRESHOLD"), any(), eq("admin1"));
    }

    @Test
    void opsEndpointsAreAdminOnly() throws Exception {
        mvc.perform(post("/api/v1/ops/dlq/sentinel.transactions.ingested/replay").with(as("senior1", "SENIOR_ANALYST")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unlistedEndpointIsDeniedEvenForAdmin() throws Exception {
        mvc.perform(get("/api/v1/not-a-rule-yet").with(as("admin1", "ADMIN"))).andExpect(status().isForbidden());
    }
}
