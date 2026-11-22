package org.example.synchro.services.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class MusicBrainzUnavailableException extends ResponseStatusException {
    private final long retryAfterSeconds;

    public MusicBrainzUnavailableException(long retryAfterSeconds) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "MusicBrainz está temporariamente indisponível. Tente novamente em instantes.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
