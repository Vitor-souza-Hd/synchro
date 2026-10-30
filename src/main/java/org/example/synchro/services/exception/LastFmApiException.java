package org.example.synchro.services.exception;

public class LastFmApiException extends RuntimeException {
    private final int code;

    public LastFmApiException(int code) {
        super("O Last.fm retornou o erro " + code + ".");
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
