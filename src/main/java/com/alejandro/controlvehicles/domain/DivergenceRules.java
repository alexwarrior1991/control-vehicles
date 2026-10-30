package com.alejandro.controlvehicles.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Each rule compares one declared fact with what the odometer, Google or the receipts say. Pure functions. */
public final class DivergenceRules {

    private DivergenceRules() {
    }

    /** Odometer km against Google km: unrecorded use, detours or a wrong odometer reading. */
    public static Optional<Divergence> distance(Trip trip, int odometerKm, DivergenceThresholds thresholds) {
        if (trip.route() == null || trip.route().distanceMeters() == 0) {
            return Optional.empty();
        }
        double googleKm = trip.route().distanceMeters() / 1000.0;
        double gap = odometerKm - googleKm;
        double percent = gap / googleKm * 100;
        if (Math.abs(gap) <= thresholds.distanceMinKm() || Math.abs(percent) <= thresholds.distancePercent()) {
            return Optional.empty();
        }
        Severity severity = Math.abs(percent) > 2.0 * thresholds.distancePercent() ? Severity.HIGH : Severity.WARNING;
        String detail = String.format(Locale.ENGLISH, "Odometer %,d km vs Google %,.1f km (%+,.1f km, %+.0f%%)",
                odometerKm, googleKm, gap, percent);
        return Optional.of(onTrip(DivergenceKind.DISTANCE_MISMATCH, severity, trip, detail));
    }

    /** Declared start and end against Google's driving time. */
    public static Optional<Divergence> duration(Trip trip, DivergenceThresholds thresholds) {
        if (trip.route() == null || trip.route().durationSeconds() == 0) {
            return Optional.empty();
        }
        long declared = trip.declaredDuration().toMinutes();
        long google = Math.round(trip.route().durationSeconds() / 60.0);
        long gap = declared - google;
        double percent = google == 0 ? 0 : gap * 100.0 / google;
        if (Math.abs(gap) < thresholds.durationMinMinutes() || Math.abs(percent) <= thresholds.durationPercent()) {
            return Optional.empty();
        }
        // Mucho más largo puede ser una espera; más corto que la mitad de lo que dice Google no es posible.
        Severity severity = declared * 2 < google ? Severity.HIGH : Severity.WARNING;
        String detail = String.format(Locale.ENGLISH, "Declared %s vs Google %s (%+d min, %+.0f%%)",
                Formats.duration(Duration.ofMinutes(declared)), Formats.duration(Duration.ofMinutes(google)), gap,
                percent);
        return Optional.of(onTrip(DivergenceKind.DURATION_MISMATCH, severity, trip, detail));
    }

    /** Declared tolls against Google's estimate. */
    public static Optional<Divergence> tolls(Trip trip, DivergenceThresholds thresholds) {
        if (trip.route() == null) {
            return Optional.empty();
        }
        BigDecimal declared = trip.tollsPaid();
        return switch (trip.route().tollStatus()) {
            case NONE -> declared.signum() > 0
                    ? Optional.of(onTrip(DivergenceKind.TOLL_MISMATCH, Severity.INFO, trip,
                    String.format(Locale.ENGLISH, "Declared %s € of tolls, Google's route has none",
                            Formats.money(declared))))
                    : Optional.empty();
            case PRICED -> {
                BigDecimal estimate = trip.route().tollEstimate();
                BigDecimal gap = declared.subtract(estimate);
                if (gap.abs().compareTo(thresholds.tollToleranceEur()) <= 0) {
                    yield Optional.empty();
                }
                yield Optional.of(onTrip(DivergenceKind.TOLL_MISMATCH,
                        gap.signum() > 0 ? Severity.WARNING : Severity.INFO, trip,
                        String.format(Locale.ENGLISH, "Declared %s € vs Google %s € of tolls", Formats.money(declared),
                                Formats.money(estimate))));
            }
            case UNPRICED -> Optional.empty();
        };
    }

    /** A trip that starts below where the previous one ended, or not above where it started. */
    public static Optional<Divergence> odometerSequence(OdometerLedger.Entry entry) {
        if (!entry.notIncreasing()) {
            return Optional.empty();
        }
        Trip previous = entry.previous();
        String detail = previous.endOdometerKm() != null
                ? String.format(Locale.ENGLISH, "Starts at %,d km, previous trip (%s) ended at %,d km",
                entry.trip().startOdometerKm(), Formats.dateTime(previous.startAt()), previous.endOdometerKm())
                : String.format(Locale.ENGLISH, "Starts at %,d km, previous trip (%s) started at %,d km",
                entry.trip().startOdometerKm(), Formats.dateTime(previous.startAt()), previous.startOdometerKm());
        return Optional.of(onTrip(DivergenceKind.ODOMETER_NOT_INCREASING, Severity.HIGH, entry.trip(), detail));
    }

    /**
     * Kilometres between a trip's noted end and the next trip's start: the vehicle moved and nobody registered it.
     * Reported on the trip after which it happened, whose driver had the vehicle last.
     */
    public static Optional<Divergence> unrecordedUse(OdometerLedger.Entry entry, DivergenceThresholds thresholds) {
        Integer gap = entry.unrecordedKm();
        if (gap == null || gap <= thresholds.unrecordedMinKm()) {
            return Optional.empty();
        }
        Severity severity = gap > 10 * Math.max(1, thresholds.unrecordedMinKm()) ? Severity.HIGH : Severity.WARNING;
        String detail = String.format(Locale.ENGLISH,
                "%,d km unrecorded: ended at %,d km, the next trip (%s) started at %,d km", gap,
                entry.trip().endOdometerKm(), Formats.dateTime(entry.next().startAt()),
                entry.next().startOdometerKm());
        return Optional.of(onTrip(DivergenceKind.UNRECORDED_USE, severity, entry.trip(), detail));
    }

    /**
     * Trips that overlap in time, given all the trips of one vehicle or of one driver. Each overlap is reported
     * on the later trip.
     */
    public static List<Divergence> overlaps(List<Trip> trips, DivergenceKind kind) {
        List<Trip> ordered = trips.stream().sorted(OdometerLedger.CHRONOLOGICAL).toList();
        List<Divergence> found = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            Trip earlier = ordered.get(i);
            for (int j = i + 1; j < ordered.size() && ordered.get(j).startAt().isBefore(earlier.endAt()); j++) {
                Trip later = ordered.get(j);
                String detail = String.format(Locale.ENGLISH, "%s–%s overlaps another trip %s–%s",
                        Formats.dateTime(later.startAt()), Formats.time(later.endTime()),
                        Formats.dateTime(earlier.startAt()), Formats.time(earlier.endTime()));
                found.add(onTrip(kind, Severity.WARNING, later, detail));
            }
        }
        return found;
    }

    /** More litres than fit in the tank (2 % margin for the filler neck). */
    public static Optional<Divergence> overCapacity(Refuel refuel, Vehicle vehicle) {
        if (vehicle.tankCapacityL() == null) {
            return Optional.empty();
        }
        BigDecimal limit = vehicle.tankCapacityL().multiply(new BigDecimal("1.02"));
        if (refuel.litres().compareTo(limit) <= 0) {
            return Optional.empty();
        }
        String detail = String.format(Locale.ENGLISH, "%s l in a %s l tank", Formats.decimal(refuel.litres()),
                Formats.decimal(vehicle.tankCapacityL()));
        return Optional.of(onRefuel(DivergenceKind.REFUEL_OVER_CAPACITY, Severity.HIGH, refuel, detail));
    }

    /**
     * A refuel's odometer must lie between the start of the last trip before it and the start of the first trip
     * after it.
     */
    public static Optional<Divergence> refuelSequence(Refuel refuel, Trip tripBefore, Trip tripAfter) {
        if (tripBefore != null && refuel.odometerKm() < tripBefore.startOdometerKm()) {
            return Optional.of(onRefuel(DivergenceKind.REFUEL_ODOMETER_OUT_OF_SEQUENCE, Severity.WARNING, refuel,
                    String.format(Locale.ENGLISH, "Refuel at %,d km, but the trip of %s started at %,d km",
                            refuel.odometerKm(), Formats.dateTime(tripBefore.startAt()),
                            tripBefore.startOdometerKm())));
        }
        if (tripAfter != null && refuel.odometerKm() > tripAfter.startOdometerKm()) {
            return Optional.of(onRefuel(DivergenceKind.REFUEL_ODOMETER_OUT_OF_SEQUENCE, Severity.WARNING, refuel,
                    String.format(Locale.ENGLISH, "Refuel at %,d km, but the next trip (%s) started at %,d km",
                            refuel.odometerKm(), Formats.dateTime(tripAfter.startAt()),
                            tripAfter.startOdometerKm())));
        }
        return Optional.empty();
    }

    /** Measured consumption between two full tanks against the vehicle's nominal one. */
    public static Optional<Divergence> consumption(ConsumptionSegment segment, Vehicle vehicle,
                                                   DivergenceThresholds thresholds) {
        BigDecimal measured = segment.litresPer100Km();
        BigDecimal nominal = vehicle.nominalConsumptionL100();
        double percent = measured.subtract(nominal).multiply(BigDecimal.valueOf(100))
                .divide(nominal, 1, RoundingMode.HALF_UP).doubleValue();
        if (Math.abs(percent) <= thresholds.consumptionPercent()) {
            return Optional.empty();
        }
        Severity severity = percent > 2.0 * thresholds.consumptionPercent() ? Severity.HIGH
                : percent > 0 ? Severity.WARNING : Severity.INFO;
        String detail = String.format(Locale.ENGLISH,
                "%s l/100 km measured vs %s nominal (%+.0f%%) over %,d km from %s to %s",
                Formats.decimal(measured), Formats.decimal(nominal), percent, segment.km(),
                Formats.date(segment.from().refuelAt().toLocalDate()),
                Formats.date(segment.to().refuelAt().toLocalDate()));
        Refuel to = segment.to();
        return Optional.of(new Divergence(DivergenceKind.CONSUMPTION_MISMATCH, severity, to.refuelAt().toLocalDate(),
                to.vehicleId(), to.driverId(), null, to.id(), detail));
    }

    private static Divergence onTrip(DivergenceKind kind, Severity severity, Trip trip, String detail) {
        return new Divergence(kind, severity, trip.date(), trip.vehicleId(), trip.driverId(), trip.id(), null, detail);
    }

    private static Divergence onRefuel(DivergenceKind kind, Severity severity, Refuel refuel, String detail) {
        return new Divergence(kind, severity, refuel.refuelAt().toLocalDate(), refuel.vehicleId(), refuel.driverId(),
                null, refuel.id(), detail);
    }
}
