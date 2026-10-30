package com.alejandro.controlvehicles.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The trips of one vehicle in the order they happened, each with the distance the odometer says it covered:
 * the next trip's start minus its own. The last trip has no distance yet ({@code null}), and neither has one
 * followed by a start that does not increase.
 */
public final class OdometerLedger {

    /** Chronological order; equal starts are broken by odometer and then id, so the order is stable. */
    public static final Comparator<Trip> CHRONOLOGICAL = Comparator.comparing(Trip::startAt)
            .thenComparingInt(Trip::startOdometerKm)
            .thenComparing(trip -> trip.id() == null ? Long.MAX_VALUE : trip.id());

    public record Entry(Trip trip, Trip previous, Trip next, Integer odometerKm) {

        public boolean pending() {
            return next == null;
        }

        /** The start of this trip is not above the previous one's. */
        public boolean notIncreasing() {
            return previous != null && trip.startOdometerKm() <= previous.startOdometerKm();
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
            if (next != null && next.startOdometerKm() > trip.startOdometerKm()) {
                km = next.startOdometerKm() - trip.startOdometerKm();
            }
            entries.add(new Entry(trip, previous, next, km));
        }
        return entries;
    }
}
