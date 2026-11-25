package com.neobank.coachservice.exception;

public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException() {
        super("La clé d’idempotence a déjà été utilisée pour une autre opération.");
    }
}
