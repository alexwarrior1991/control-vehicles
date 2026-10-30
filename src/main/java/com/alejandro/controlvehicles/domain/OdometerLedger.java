package com.alejandro.controlvehicles.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The trips of one vehicle in the order they happened, each with the distance the odometer says it covered.
 * With its end odometer noted, that is end minus start, and the kilometres between its end and the next trip's
 * start are {@code unrecordedKm}: the vehicle moved and nobody registered it. Without it, the distance is the next
 * trip's start minus its own, unrecorded use included and indistinguishable. A trip without end and without a
 * next trip has no distance yet ({@code null}, pending), and neither has one followed by a start below where it
 * ended (or started).
 */
public final class OdometerLedger {

    /** Chronological order; equal starts are broken by odometer and then id, so the order is stable. */
    public static final Comparator<Trip> CHRONOLOGICAL = Comparator.comparing(Trip::startAt)
            .thenComparingInt(Trip::startOdometerKm)
            .thenComparing(trip -> trip.id() == null ? Long.MAX_VALUE : trip.id());

    public record Entry(Trip trip, Trip previous, Trip next, Integer odometerKm, Integer unrecordedKm) {

        public boolean pending() {
            return odometerKm == null && next == null;
        }

        /**
         * This trip starts below the previous one's end, or, when the previous one has no end, not above its
         * start.
         */
        public boolean notIncreasing() {
            if (previous == null) {
                return false;
            }
            return previous.endOdometerKm() != null
                    ? trip.startOdometerKm() < previous.endOdometerKm()
                    : trip.startOdometerKm() <= previous.startOdometerKm();
        }
    }

    private OdometerLedger() {
    }

    public static List<Entry> of(List<Trip> tripsOfOneVehicle) {
        List<Trip> ordered = tripsOfOneVehicle.stream().sorted(CHRONOLOGICAL).toList();
        List<Entry> entries = new ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            Trip trip = ordered.get(i);
            Trip previous = i > 0 ? ordered.get(i - 1) : null;
            Trip next = i + 1 < ordered.size() ? ordered.get(i + 1) : null;
            Integer km = null;
            Integer unrecorded = null;
            if (trip.endOdometerKm() != null) {
                km = trip.endOdometerKm() - trip.startOdometerKm();
                if (next != null && next.startOdometerKm() >= trip.endOdometerKm()) {
                    unrecorded = next.startOdometerKm() - trip.endOdometerKm();
                }
            } else if (next != null && next.startOdometerKm() > trip.startOdometerKm()) {
                km = next.startOdometerKm() - trip.startOdometerKm();
            }
            entries.add(new Entry(trip, previous, next, km, unrecorded));
        }
        return entries;
    }
}
