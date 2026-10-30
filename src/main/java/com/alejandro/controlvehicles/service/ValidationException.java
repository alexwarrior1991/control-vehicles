package com.alejandro.controlvehicles.service;

/** A rule the user broke; its message is shown as it is. */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
