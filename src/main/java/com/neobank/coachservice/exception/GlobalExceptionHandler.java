package com.neobank.coachservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.validation.ConstraintViolationException;
import com.neobank.coachservice.sca.ScaProviderUnavailableException;
import com.neobank.coachservice.monitoring.SecurityMonitoringMetrics;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final SecurityMonitoringMetrics metrics;

    public GlobalExceptionHandler() {
        this.metrics = null;
    }

    @Autowired
    public GlobalExceptionHandler(SecurityMonitoringMetrics metrics) {
        this.metrics = metrics;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getAllErrors().stream()
                .map(org.springframework.context.support.DefaultMessageSourceResolvable::getDefaultMessage)
                .toList();

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "invalid-request-data", "Données invalides",
                "Les données envoyées sont invalides.");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "invalid-request-parameter", "Paramètres invalides",
                "Un ou plusieurs paramètres de la requête sont hors limites.");
        problem.setProperty("errors", ex.getConstraintViolations().stream()
                .map(jakarta.validation.ConstraintViolation::getMessage)
                .toList());
        return problem;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ProblemDetail handleMalformedRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "malformed-request", "Requête invalide",
                "Le corps ou les paramètres de la requête sont absents ou mal formés.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed", "Méthode non autorisée",
                "Cette méthode HTTP n’est pas prise en charge pour cette ressource.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-media-type", "Format non pris en charge",
                "Le format du contenu envoyé n’est pas pris en charge.");
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleNotFound(EntityNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "resource-not-found", "Ressource introuvable",
                "La ressource demandée est introuvable ou inaccessible.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFound(NoResourceFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "resource-not-found", "Ressource introuvable",
                "La ressource demandée est introuvable.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Requête invalide",
                "La requête ne peut pas être traitée avec les valeurs fournies.");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        return problem(HttpStatus.CONFLICT, "operation-conflict", "Opération impossible",
                "L’opération est incompatible avec l’état actuel de la ressource.");
    }

    @ExceptionHandler(RequestRateLimitExceededException.class)
    public ProblemDetail handleRateLimitExceeded(RequestRateLimitExceededException ex) {
        if (metrics != null) {
            metrics.rateLimitExceeded("salary-detection".equals(ex.getOperation()) ? "salary_detection" : "ai_analysis");
        }
        return problem(HttpStatus.TOO_MANY_REQUESTS, "rate-limit-exceeded", "Trop de requêtes",
                "La limite d’utilisation de cette opération est atteinte. Réessayez plus tard.");
    }

    @ExceptionHandler(AiConsentRequiredException.class)
    public ProblemDetail handleAiConsentRequired(AiConsentRequiredException ex) {
        return problem(HttpStatus.FORBIDDEN, "ai-consent-required", "Consentement requis",
                "Le consentement au traitement IA doit être actif pour cette opération.");
    }

    @ExceptionHandler(ScaVerificationFailedException.class)
    public ProblemDetail handleScaVerificationFailed(ScaVerificationFailedException ex) {
        incrementSca("rejected");
        return scaProblem(HttpStatus.BAD_REQUEST, "sca-verification-failed", ex.getMessage());
    }

    @ExceptionHandler(ScaChallengeExpiredException.class)
    public ProblemDetail handleScaChallengeExpired(ScaChallengeExpiredException ex) {
        incrementSca("expired");
        return scaProblem(HttpStatus.GONE, "sca-challenge-expired", ex.getMessage());
    }

    @ExceptionHandler(ScaAttemptsExceededException.class)
    public ProblemDetail handleScaAttemptsExceeded(ScaAttemptsExceededException ex) {
        incrementSca("attempts_exceeded");
        return scaProblem(HttpStatus.TOO_MANY_REQUESTS, "sca-attempts-exceeded", ex.getMessage());
    }

    @ExceptionHandler(ScaProviderUnavailableException.class)
    public ProblemDetail handleScaProviderUnavailable(ScaProviderUnavailableException ex) {
        if (metrics != null) {
            metrics.scaEvent("provider_unavailable");
            metrics.providerFailure("sca");
        }
        return scaProblem(HttpStatus.SERVICE_UNAVAILABLE, "sca-provider-unavailable", ex.getMessage());
    }

    @ExceptionHandler(RestClientException.class)
    public ProblemDetail handleExternalProviderUnavailable(RestClientException ex) {
        if (metrics != null) {
            metrics.providerFailure("transaction_service");
        }
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "external-service-unavailable", "Service externe indisponible",
                "Un service nécessaire est temporairement indisponible. Réessayez plus tard.");
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ProblemDetail handleIdempotencyKeyConflict(IdempotencyKeyConflictException ex) {
        return problem(HttpStatus.CONFLICT, "idempotency-key-conflict", "Conflit de clé d’idempotence",
                "Cette clé d’idempotence a déjà été utilisée pour une autre opération.");
    }

    @ExceptionHandler(DataErasureBlockedException.class)
    public ProblemDetail handleDataErasureBlocked(DataErasureBlockedException ex) {
        return problem(HttpStatus.CONFLICT, "data-erasure-blocked", "Effacement différé",
                "L’effacement est impossible tant qu’un coffre contient un solde actif.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        String errorId = UUID.randomUUID().toString();
        log.error("Unhandled application error id={} type={}", errorId, ex.getClass().getSimpleName());
        ProblemDetail problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Erreur interne",
                "Une erreur interne est survenue. Réessayez plus tard.");
        problem.setProperty("errorId", errorId);
        return problem;
    }

    private void incrementSca(String outcome) {
        if (metrics != null) {
            metrics.scaEvent(outcome);
        }
    }

    private ProblemDetail scaProblem(HttpStatus status, String error, String detail) {
        return problem(status, error, "Erreur de validation SCA", detail);
    }

    private ProblemDetail problem(HttpStatus status, String error, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://api.bank.com/errors/" + error));
        problem.setTitle(title);
        problem.setProperty("code", error);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

}