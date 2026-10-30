package com.alejandro.controlvehicles.domain;

import java.util.Locale;
import java.util.Optional;

/** Countries the fleet runs in; each one has its own fuel price source. */
public enum Country {
    ES("Spain"),
    PT("Portugal");

    private final String label;

    Country(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** The country of an ISO 3166 alpha-2 code ("es", "PT"), if it is one of ours. */
    public static Optional<Country> fromIsoCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(code.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return label;
    }
}
