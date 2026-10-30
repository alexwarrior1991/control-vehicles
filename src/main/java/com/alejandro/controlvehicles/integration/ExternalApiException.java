package com.alejandro.controlvehicles.integration;

/**
 * A call to Google or to a fuel price service that did not give a usable answer. A Google error also says why, in
 * its {@code google.rpc.ErrorInfo}: {@code reason} (SERVICE_DISABLED, API_KEY_SERVICE_BLOCKED…) and the
 * {@code service} it is about; both are {@code null} otherwise.
 */
public class ExternalApiException extends RuntimeException {

    private final int status;
    private final String reason;
    private final String service;

    public ExternalApiException(String message, int status) {
        this(message, status, null, null);
    }

    public ExternalApiException(String message, int status, String reason, String service) {
        super(message);
        this.status = status;
        this.reason = reason;
        this.service = service;
    }

    public ExternalApiException(String message, Throwable cause) {
        super(message, cause);
        this.status = 0;
        this.reason = null;
        this.service = null;
    }

    /** The HTTP status, or 0 when there was no answer at all. */
    public int status() {
        return status;
    }

    public String reason() {
        return reason;
    }

    public String service() {
        return service;
    }
}
