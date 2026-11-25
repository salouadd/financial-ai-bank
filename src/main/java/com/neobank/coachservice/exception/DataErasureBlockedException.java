package com.neobank.coachservice.exception;

public class DataErasureBlockedException extends RuntimeException {
    public DataErasureBlockedException() {
        super("L’effacement est impossible tant qu’un coffre contient un solde actif.");
    }
}
