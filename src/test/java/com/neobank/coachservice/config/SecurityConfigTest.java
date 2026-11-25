package com.neobank.coachservice.config;

import com.neobank.coachservice.SecurityConfig;
import com.neobank.coachservice.monitoring.SecurityMonitoringMetrics;
import com.neobank.coachservice.security.SecurityProblemHandlers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.MediaType;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(
        controllers = SecurityConfigTest.TestEndpoints.class,
        properties = {
                "security.jwt.issuer=https://identity.example.test",
                "security.jwt.audience=financial-coach-service",
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/jwks"
        }
)
@Import({SecurityConfig.class, SecurityProblemHandlers.class, SecurityConfigTest.TestEndpoints.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private SecurityMonitoringMetrics metrics;

    @Test
    void actuatorEndpointsRejectAnonymousRequests() throws Exception {
        mockMvc.perform(get("/actuator/test"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("authentication-required"));
        verify(metrics).authenticationFailure("unauthorized");
    }

    @Test
    void actuatorEndpointsRequireAdminRole() throws Exception {
        mockJwtToken("user-token", "USER");
        mockJwtToken("admin-token", "ADMIN");

        mockMvc.perform(get("/actuator/test").header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("access-denied"));
        verify(metrics).authenticationFailure("forbidden");

        mockMvc.perform(get("/actuator/test").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void businessEndpointsContinueToRequireUserRole() throws Exception {
        mockJwtToken("user-token", "USER");
        mockJwtToken("admin-token", "ADMIN");
        mockJwtToken("no-role-token", null);

        mockMvc.perform(get("/api/v1/coach/test"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/coach/test").header("Authorization", "Bearer user-token"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/coach/test").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/coach/test").header("Authorization", "Bearer no-role-token"))
                .andExpect(status().isForbidden());
    }

    private void mockJwtToken(String tokenValue, String role) {
        Jwt.Builder builder = Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .claim("sub", "test-user");
        if (role != null) {
            builder.claim("role", role);
        }
        when(jwtDecoder.decode(tokenValue)).thenReturn(builder.build());
    }

    @RestController
    static class TestEndpoints {

        @GetMapping("/actuator/test")
        String actuatorTest() {
            return "actuator";
        }

        @GetMapping("/api/v1/coach/test")
        String businessTest() {
            return "business";
        }
    }
}
