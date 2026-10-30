package com.alejandro.controlvehicles.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Fuel money: kilometres × litres per 100 km × price per litre, in euros with cents. */
public final class CostCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private CostCalculator() {
    }

    public static BigDecimal litres(BigDecimal km, BigDecimal litresPer100Km) {
        return km.multiply(litresPer100Km).divide(HUNDRED, 3, RoundingMode.HALF_UP);
    }

    public static BigDecimal fuelCost(BigDecimal km, BigDecimal litresPer100Km, BigDecimal pricePerLitre) {
        return km.multiply(litresPer100Km).multiply(pricePerLitre).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
