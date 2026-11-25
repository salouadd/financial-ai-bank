package com.neobank.coachservice.sca;

public class ScaProviderUnavailableException extends RuntimeException {

    public ScaProviderUnavailableException() {
        super("Le service de validation SCA est indisponible ou non configuré.");
    }

    public ScaProviderUnavailableException(Throwable cause) {
        super("Le service de validation SCA est indisponible.", cause);
    }
}
