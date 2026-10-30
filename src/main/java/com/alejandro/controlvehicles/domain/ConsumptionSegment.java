package com.alejandro.controlvehicles.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** What a vehicle burnt between two full-tank refuels: every litre put in after the first, up to the second. */
public record ConsumptionSegment(long vehicleId, Refuel from, Refuel to, int km, BigDecimal litres) {

    public BigDecimal litresPer100Km() {
        return litres.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(km), 2, RoundingMode.HALF_UP);
    }
}
