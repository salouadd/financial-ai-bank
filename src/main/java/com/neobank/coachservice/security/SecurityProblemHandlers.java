package com.neobank.coachservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.coachservice.monitoring.SecurityMonitoringMetrics;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;

@Component
public class SecurityProblemHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final SecurityMonitoringMetrics metrics;

    public SecurityProblemHandlers(ObjectMapper objectMapper, SecurityMonitoringMetrics metrics) {
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            org.springframework.security.core.AuthenticationException authException) throws IOException {
        metrics.authenticationFailure("unauthorized");
        writeProblem(response, HttpStatus.UNAUTHORIZED, "authentication-required", "Authentification requise",
                "Une authentification valide est nécessaire pour accéder à cette ressource.");
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        metrics.authenticationFailure("forbidden");
        writeProblem(response, HttpStatus.FORBIDDEN, "access-denied", "Accès refusé",
                "Vous ne disposez pas des droits nécessaires pour accéder à cette ressource.");
    }

    private void writeProblem(
            HttpServletResponse response,
            HttpStatus status,
            String error,
            String title,
            String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://api.bank.com/errors/" + error));
        problem.setTitle(title);
        problem.setProperty("code", error);
        problem.setProperty("timestamp", Instant.now());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        if (status == HttpStatus.UNAUTHORIZED) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
