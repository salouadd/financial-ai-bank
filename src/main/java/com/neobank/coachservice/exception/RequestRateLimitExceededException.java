package com.neobank.coachservice.exception;

public class RequestRateLimitExceededException extends RuntimeException {

    private final String operation;

    public RequestRateLimitExceededException(String operation) {
        super("La limite d'utilisation de l'opération " + operation + " est atteinte. Réessayez plus tard.");
        this.operation = operation;
    }

    public String getOperation() {
        return operation;
    }
}
