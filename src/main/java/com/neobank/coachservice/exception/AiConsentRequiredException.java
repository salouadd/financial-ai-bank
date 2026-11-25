package com.neobank.coachservice.exception;

public class AiConsentRequiredException extends RuntimeException {

    public AiConsentRequiredException() {
        super("Le consentement actif au traitement IA est requis pour cette analyse.");
    }
}
