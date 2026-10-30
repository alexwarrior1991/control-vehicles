package com.alejandro.controlvehicles.domain;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

/**
 * A vehicle of the fleet. {@code nominalConsumptionL100} is what it should burn (litres per 100 km), the
 * reference for every expected cost; {@code initialOdometerKm} is its odometer when it entered the application.
 */
public record Vehicle(Long id, String plate, String brand, String model, FuelType fuelType,
                      BigDecimal nominalConsumptionL100, BigDecimal tankCapacityL, int initialOdometerKm,
                      boolean active, String notes) {

    public Vehicle {
        plate = Texts.required(plate, "plate").toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        brand = Texts.trimToNull(brand);
        model = Texts.trimToNull(model);
        Objects.requireNonNull(fuelType, "fuel type is required");
        Objects.requireNonNull(nominalConsumptionL100, "nominal consumption is required");
        if (nominalConsumptionL100.signum() <= 0) {
            throw new IllegalArgumentException("nominal consumption must be greater than zero");
        }
        if (tankCapacityL != null && tankCapacityL.signum() <= 0) {
            throw new IllegalArgumentException("tank capacity must be greater than zero");
        }
        if (initialOdometerKm < 0) {
            throw new IllegalArgumentException("initial odometer cannot be negative");
        }
        notes = Texts.trimToNull(notes);
    }

    public Vehicle withId(long newId) {
        return new Vehicle(newId, plate, brand, model, fuelType, nominalConsumptionL100, tankCapacityL,
                initialOdometerKm, active, notes);
    }

    public Vehicle withActive(boolean value) {
        return new Vehicle(id, plate, brand, model, fuelType, nominalConsumptionL100, tankCapacityL,
                initialOdometerKm, value, notes);
    }

    /** "1234 ABC · Renault Kangoo". */
    public String label() {
        String name = String.join(" ", java.util.stream.Stream.of(brand, model).filter(Objects::nonNull).toList());
        return name.isEmpty() ? plate : plate + " · " + name;
    }

    @Override
    public String toString() {
        return label();
    }
}
