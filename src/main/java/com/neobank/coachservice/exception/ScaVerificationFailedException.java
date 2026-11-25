package com.neobank.coachservice.exception;

public class ScaVerificationFailedException extends RuntimeException {

    public ScaVerificationFailedException() {
        super("Le code SCA est invalide.");
    }
}
