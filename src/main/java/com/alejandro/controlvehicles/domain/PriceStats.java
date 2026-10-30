package com.alejandro.controlvehicles.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Optional;

/** Average, minimum and maximum of a set of station prices. */
public record PriceStats(BigDecimal average, BigDecimal min, BigDecimal max, int count) {

    public static Optional<PriceStats> of(Collection<BigDecimal> prices) {
        if (prices.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal min = null;
        BigDecimal max = null;
        for (BigDecimal price : prices) {
            sum = sum.add(price);
            min = min == null || price.compareTo(min) < 0 ? price : min;
            max = max == null || price.compareTo(max) > 0 ? price : max;
        }
        BigDecimal average = sum.divide(BigDecimal.valueOf(prices.size()), 3, RoundingMode.HALF_UP);
        return Optional.of(new PriceStats(average, min, max, prices.size()));
    }
}
