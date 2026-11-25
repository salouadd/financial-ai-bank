package com.neobank.coachservice.exception;

public class ScaAttemptsExceededException extends RuntimeException {

    public ScaAttemptsExceededException() {
        super("Le nombre maximal de tentatives SCA est atteint.");
    }
}
