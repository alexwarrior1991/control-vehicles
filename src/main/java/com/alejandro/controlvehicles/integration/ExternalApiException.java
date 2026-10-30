package com.alejandro.controlvehicles.integration;

/** A call to Google or to a fuel price service that did not give a usable answer. */
public class ExternalApiException extends RuntimeException {

    private final int status;

    public ExternalApiException(String message, int status) {
        super(message);
        this.status = status;
    }

    public ExternalApiException(String message, Throwable cause) {
        super(message, cause);
        this.status = 0;
    }

    /** The HTTP status, or 0 when there was no answer at all. */
    public int status() {
        return status;
    }
}
