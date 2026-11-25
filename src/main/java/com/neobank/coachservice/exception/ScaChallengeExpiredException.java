package com.neobank.coachservice.exception;

public class ScaChallengeExpiredException extends RuntimeException {

    public ScaChallengeExpiredException() {
        super("Le défi SCA est expiré ou a déjà été utilisé.");
    }
}
