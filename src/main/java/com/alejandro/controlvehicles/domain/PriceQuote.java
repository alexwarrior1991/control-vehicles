package com.alejandro.controlvehicles.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** The fuel price used for the costs of one trip, and where it came from. */
public record PriceQuote(BigDecimal pricePerLitre, String source) {

    public static final String MANUAL = "Manual";

    public PriceQuote {
        Objects.requireNonNull(pricePerLitre, "price is required");
        if (pricePerLitre.signum() <= 0) {
            throw new IllegalArgumentException("fuel price must be greater than zero");
        }
        source = Texts.required(source, "price source");
    }

    public static PriceQuote manual(BigDecimal price) {
        return new PriceQuote(price, MANUAL);
    }

    public boolean isManual() {
        return MANUAL.equals(source);
    }
}
