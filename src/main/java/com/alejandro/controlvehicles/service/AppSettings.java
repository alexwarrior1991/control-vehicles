package com.alejandro.controlvehicles.service;

import com.alejandro.controlvehicles.domain.Country;
import com.alejandro.controlvehicles.domain.DivergenceThresholds;

import java.util.Objects;

/**
 * The rules of the application. They live in the database, so whoever receives an export sees the same
 * divergences. {@code preferredBrand} is the brand whose stations set the expected price (Galp).
 */
public record AppSettings(DivergenceThresholds thresholds, int priceRadiusKm, String preferredBrand,
                          Country defaultCountry) {

    public static final AppSettings DEFAULTS = new AppSettings(DivergenceThresholds.DEFAULTS, 25, "GALP", Country.ES);

    public AppSettings {
        Objects.requireNonNull(thresholds);
        if (priceRadiusKm <= 0) {
            throw new IllegalArgumentException("the price radius must be greater than zero");
        }
        preferredBrand = preferredBrand == null || preferredBrand.isBlank() ? null : preferredBrand.trim();
        Objects.requireNonNull(defaultCountry);
    }
}
