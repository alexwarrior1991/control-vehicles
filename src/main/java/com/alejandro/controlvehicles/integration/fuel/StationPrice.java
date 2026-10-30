package com.alejandro.controlvehicles.integration.fuel;

import java.math.BigDecimal;
import java.util.Locale;

/** The price of one fuel at one station. */
public record StationPrice(String name, String brand, double latitude, double longitude, String locality,
                           BigDecimal price) {

    public boolean isBrand(String wanted) {
        return wanted != null && brand != null
                && brand.toUpperCase(Locale.ROOT).contains(wanted.toUpperCase(Locale.ROOT));
    }
}
