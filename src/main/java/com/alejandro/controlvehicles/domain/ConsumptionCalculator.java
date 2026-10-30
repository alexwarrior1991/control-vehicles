package com.alejandro.controlvehicles.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Measured consumption by the full-tank method: from one full refuel to the next, the litres of every refuel
 * after the first (partial ones included) over the kilometres between them. A partial refuel before the first
 * full one counts for nothing, because the tank level it started from is unknown. A refuel whose odometer was
 * estimated is never a boundary, even if full: its litres count inside the stretch that contains it, and full to
 * full across it is still right.
 */
public final class ConsumptionCalculator {

    private ConsumptionCalculator() {
    }

    public static List<ConsumptionSegment> segments(long vehicleId, List<Refuel> refuelsOfOneVehicle) {
        // Un repostaje con km estimado se ordena por su momento entre los de km leído.
        List<Refuel> ordered = refuelsOfOneVehicle.stream().sorted(Comparator.comparing(Refuel::refuelAt)
                .thenComparingInt(Refuel::odometerKm)).toList();
        List<ConsumptionSegment> segments = new ArrayList<>();
        Refuel lastFull = null;
        BigDecimal litres = BigDecimal.ZERO;
        for (Refuel refuel : ordered) {
            if (lastFull != null) {
                litres = litres.add(refuel.litres());
            }
            if (refuel.fullTank() && !refuel.odometerEstimated()) {
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
