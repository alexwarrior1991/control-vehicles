package com.alejandro.controlvehicles.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A refuel as it appears on the receipt, with the odometer at that moment. The driver is optional.
 * {@code odometerEstimated}: the odometer was not on the receipt and was estimated from the trips (an imported card
 * statement); such a refuel never sets a consumption boundary nor an odometer check.
 */
public record Refuel(Long id, long vehicleId, Long driverId, LocalDateTime refuelAt, int odometerKm,
                     boolean odometerEstimated, BigDecimal litres, BigDecimal totalAmount, boolean fullTank, String station, String brand,
                     Country country, String notes) {

    public Refuel {
        Objects.requireNonNull(refuelAt, "date is required");
        if (odometerKm < 0) {
            throw new IllegalArgumentException("odometer cannot be negative");
        }
        Objects.requireNonNull(litres, "litres are required");
        Objects.requireNonNull(totalAmount, "amount is required");
        if (litres.signum() <= 0 || totalAmount.signum() <= 0) {
            throw new IllegalArgumentException("litres and amount must be greater than zero");
        }
        station = Texts.trimToNull(station);
        brand = Texts.trimToNull(brand);
        notes = Texts.trimToNull(notes);
    }

    public BigDecimal pricePerLitre() {
        return totalAmount.divide(litres, 3, RoundingMode.HALF_UP);
    }

    public Refuel withId(long newId) {
        return new Refuel(newId, vehicleId, driverId, refuelAt, odometerKm, odometerEstimated, litres, totalAmount, fullTank, station,
                brand, country, notes);
    }
}
