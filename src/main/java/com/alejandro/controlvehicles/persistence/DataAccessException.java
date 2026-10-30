package com.alejandro.controlvehicles.persistence;

/** An SQL failure, unchecked so that services and screens deal with one kind of error. */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataAccessException(String message) {
        super(message);
    }
}
