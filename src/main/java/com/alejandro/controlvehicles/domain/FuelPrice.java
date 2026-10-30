package com.alejandro.controlvehicles.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A country-wide price of one fuel on one day, for one brand ({@code null} = every brand): the history the
 * application keeps when no station price is available for a trip.
 */
public record FuelPrice(Long id, LocalDate date, Country country, FuelType fuelType, String brand,
                        BigDecimal averagePrice, BigDecimal minPrice, BigDecimal maxPrice, int stations,
                        PriceSource source, Instant fetchedAt) {

    public FuelPrice {
        Objects.requireNonNull(date, "date is required");
        Objects.requireNonNull(country, "country is required");
        Objects.requireNonNull(fuelType, "fuel type is required");
        Objects.requireNonNull(averagePrice, "price is required");
        if (averagePrice.signum() <= 0) {
            throw new IllegalArgumentException("price must be greater than zero");
        }
        brand = Texts.trimToNull(brand);
        minPrice = minPrice == null ? averagePrice : minPrice;
        maxPrice = maxPrice == null ? averagePrice : maxPrice;
        Objects.requireNonNull(source, "source is required");
        fetchedAt = fetchedAt == null ? Instant.now() : fetchedAt;
    }

    public static FuelPrice manual(LocalDate date, Country country, FuelType fuelType, String brand,
                                   BigDecimal price) {
        return new FuelPrice(null, date, country, fuelType, brand, price, price, price, 0, PriceSource.MANUAL,
                Instant.now());
    }
}
