package com.alejandro.controlvehicles.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * The month of each vehicle and each driver. "Expected" is what the trips should have cost by Google's
 * distance; "odometer" what they cost by the odometer's distance (both at the vehicle's nominal consumption and
 * the trip's fuel price); "real" what the refuels of the month cost.
 */
public record MonthlyReport(YearMonth month, List<Line> vehicles, List<Line> drivers, Line total) {

    /**
     * One row. {@code odometerKm} is the trips' own distance; {@code unrecordedKm} what the vehicle did between a
     * trip's noted end and the next start (for a driver: after their trips, while they had it last).
     * {@code measuredL100} only exists for vehicles with two full tanks in the month; {@code realSpend} and
     * {@code litresRefuelled} only count, for a driver, the refuels that name them.
     */
    public record Line(String name, int trips, int pendingTrips, int tripsWithoutPrice, int odometerKm,
                       int unrecordedKm, BigDecimal googleKm, BigDecimal gapKm, BigDecimal nominalL100, BigDecimal measuredL100,
                       BigDecimal litresRefuelled, BigDecimal realSpend, BigDecimal expectedCost,
                       BigDecimal odometerCost, BigDecimal tollsDeclared, BigDecimal tollsEstimated,
                       int divergences) {

        /** Real spend minus expected cost: what was paid beyond what the declared trips justify. */
        public BigDecimal costGap() {
            return realSpend.subtract(expectedCost);
        }
    }
}
