package com.alejandro.controlvehicles.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Everything the application derives from the stored facts, computed in one pass over all the trips and
 * refuels: the odometer distance of each trip, the consumption between full tanks and every divergence.
 * Nothing of it is stored, so editing an old trip or refuel changes it everywhere at once.
 */
public final class FleetAnalysis {

    /**
     * {@code odometerKm}: the trip's own distance by odometer; {@code unrecordedKm}: what the vehicle did after it
     * without being registered, only known when its end odometer was noted.
     */
    public record TripAnalysis(Trip trip, Integer odometerKm, Integer unrecordedKm, boolean pending,
                               List<Divergence> divergences) {
    }

    private final Map<Long, TripAnalysis> trips;
    private final Map<Long, List<ConsumptionSegment>> segmentsByVehicle;
    private final List<Divergence> divergences;

    private FleetAnalysis(Map<Long, TripAnalysis> trips, Map<Long, List<ConsumptionSegment>> segmentsByVehicle,
                          List<Divergence> divergences) {
        this.trips = trips;
        this.segmentsByVehicle = segmentsByVehicle;
        this.divergences = divergences;
    }

    public static FleetAnalysis of(List<Vehicle> vehicles, List<Trip> allTrips, List<Refuel> allRefuels,
                                   DivergenceThresholds thresholds) {
        Map<Long, Vehicle> vehiclesById = vehicles.stream().collect(Collectors.toMap(Vehicle::id, v -> v));
        Map<Long, List<Trip>> tripsByVehicle = allTrips.stream().collect(Collectors.groupingBy(Trip::vehicleId));
        Map<Long, List<Trip>> tripsByDriver = allTrips.stream().collect(Collectors.groupingBy(Trip::driverId));
        Map<Long, List<Refuel>> refuelsByVehicle = allRefuels.stream()
                .collect(Collectors.groupingBy(Refuel::vehicleId));

        Map<Long, List<Divergence>> byTrip = new HashMap<>();
        List<Divergence> all = new ArrayList<>();
        Map<Long, OdometerLedger.Entry> entries = new HashMap<>();

        tripsByVehicle.forEach((vehicleId, trips) -> {
            for (OdometerLedger.Entry entry : OdometerLedger.of(trips)) {
                Trip trip = entry.trip();
                entries.put(trip.id(), entry);
                DivergenceRules.odometerSequence(entry).ifPresent(all::add);
                DivergenceRules.unrecordedUse(entry, thresholds).ifPresent(all::add);
                if (entry.odometerKm() != null) {
                    DivergenceRules.distance(trip, entry.odometerKm(), thresholds).ifPresent(all::add);
                }
                DivergenceRules.duration(trip, thresholds).ifPresent(all::add);
                DivergenceRules.tolls(trip, thresholds).ifPresent(all::add);
            }
            all.addAll(DivergenceRules.overlaps(trips, DivergenceKind.VEHICLE_OVERLAP));
        });
        tripsByDriver.values().forEach(trips -> all.addAll(DivergenceRules.overlaps(trips,
                DivergenceKind.DRIVER_OVERLAP)));

        Map<Long, List<ConsumptionSegment>> segments = new HashMap<>();
        refuelsByVehicle.forEach((vehicleId, refuels) -> {
            Vehicle vehicle = vehiclesById.get(vehicleId);
            List<Trip> trips = tripsByVehicle.getOrDefault(vehicleId, List.of()).stream()
                    .sorted(OdometerLedger.CHRONOLOGICAL).toList();
            for (Refuel refuel : refuels) {
                Trip before = null;
                Trip after = null;
                for (Trip trip : trips) {
                    if (trip.startAt().isAfter(refuel.refuelAt())) {
                        after = trip;
                        break;
                    }
                    before = trip;
                }
                DivergenceRules.refuelSequence(refuel, before, after).ifPresent(all::add);
                if (vehicle != null) {
                    DivergenceRules.overCapacity(refuel, vehicle).ifPresent(all::add);
                }
            }
            List<ConsumptionSegment> vehicleSegments = ConsumptionCalculator.segments(vehicleId, refuels);
            segments.put(vehicleId, vehicleSegments);
            if (vehicle != null) {
                vehicleSegments.forEach(segment -> DivergenceRules.consumption(segment, vehicle, thresholds)
                        .ifPresent(all::add));
            }
        });

        all.sort(Comparator.comparing(Divergence::date).thenComparing(Divergence::kind));
        all.stream().filter(d -> d.tripId() != null)
                .forEach(d -> byTrip.computeIfAbsent(d.tripId(), id -> new ArrayList<>()).add(d));
        Map<Long, TripAnalysis> analyses = new HashMap<>();
        entries.forEach((id, entry) -> analyses.put(id, new TripAnalysis(entry.trip(), entry.odometerKm(),
                entry.unrecordedKm(), entry.pending(), List.copyOf(byTrip.getOrDefault(id, List.of())))));
        return new FleetAnalysis(analyses, segments, List.copyOf(all));
    }

    public TripAnalysis trip(long tripId) {
        return trips.get(tripId);
    }

    public List<ConsumptionSegment> segments(long vehicleId) {
        return segmentsByVehicle.getOrDefault(vehicleId, List.of());
    }

    public List<ConsumptionSegment> allSegments() {
        return segmentsByVehicle.values().stream().flatMap(List::stream).toList();
    }

    public List<Divergence> divergences() {
        return divergences;
    }
}
