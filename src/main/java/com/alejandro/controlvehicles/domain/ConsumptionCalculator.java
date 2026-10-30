package com.alejandro.controlvehicles.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Measured consumption by the full-tank method: from one full refuel to the next, the litres of every refuel
 * after the first (partial ones included) over the kilometres between them. A partial refuel before the first
 * full one counts for nothing, because the tank level it started from is unknown.
 */
public final class ConsumptionCalculator {

    public static final Comparator<Refuel> BY_ODOMETER = Comparator.comparingInt(Refuel::odometerKm)
            .thenComparing(Refuel::refuelAt);

    private ConsumptionCalculator() {
    }

    public static List<ConsumptionSegment> segments(long vehicleId, List<Refuel> refuelsOfOneVehicle) {
        List<Refuel> ordered = refuelsOfOneVehicle.stream().sorted(BY_ODOMETER).toList();
        List<ConsumptionSegment> segments = new ArrayList<>();
        Refuel lastFull = null;
        BigDecimal litres = BigDecimal.ZERO;
        for (Refuel refuel : ordered) {
            if (lastFull != null) {
                litres = litres.add(refuel.litres());
            }
            if (refuel.fullTank()) {
                if (lastFull != null && refuel.odometerKm() > lastFull.odometerKm()) {
                    segments.add(new ConsumptionSegment(vehicleId, lastFull, refuel,
                            refuel.odometerKm() - lastFull.odometerKm(), litres));
                }
                lastFull = refuel;
                litres = BigDecimal.ZERO;
            }
        }
        return segments;
    }
}
