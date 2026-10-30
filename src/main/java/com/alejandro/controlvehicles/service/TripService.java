package com.alejandro.controlvehicles.service;

import com.alejandro.controlvehicles.domain.Driver;
import com.alejandro.controlvehicles.domain.FleetAnalysis;
import com.alejandro.controlvehicles.domain.Formats;
import com.alejandro.controlvehicles.domain.OdometerEstimator;
import com.alejandro.controlvehicles.domain.Place;
import com.alejandro.controlvehicles.domain.PriceQuote;
import com.alejandro.controlvehicles.domain.RouteEstimate;
import com.alejandro.controlvehicles.domain.Trip;
import com.alejandro.controlvehicles.domain.Vehicle;
import com.alejandro.controlvehicles.integration.ExternalApiException;
import com.alejandro.controlvehicles.integration.google.RoutesClient;
import com.alejandro.controlvehicles.persistence.DriverRepository;
import com.alejandro.controlvehicles.persistence.PlaceRepository;
import com.alejandro.controlvehicles.persistence.RefuelRepository;
import com.alejandro.controlvehicles.persistence.TripRepository;
import com.alejandro.controlvehicles.persistence.VehicleRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registering a trip: the declared facts are always saved; Google's route and the fuel price are added when the
 * services answer, and can be recalculated later. Calls Google, so it must not run on the JavaFX thread.
 */
public final class TripService {

    private final TripRepository trips;
    private final VehicleRepository vehicles;
    private final DriverRepository drivers;
    private final PlaceRepository places;
    private final RefuelRepository refuels;
    private final RoutesClient routes;
    private final FuelPriceService prices;
    private final AnalysisService analysis;

    public TripService(TripRepository trips, VehicleRepository vehicles, DriverRepository drivers,
                       PlaceRepository places, RefuelRepository refuels, RoutesClient routes,
                       FuelPriceService prices, AnalysisService analysis) {
        this.trips = trips;
        this.vehicles = vehicles;
        this.drivers = drivers;
        this.places = places;
        this.refuels = refuels;
        this.routes = routes;
        this.prices = prices;
        this.analysis = analysis;
    }

    /**
     * Saves a trip. The route is asked again when the trip is new, has none or changed places; the price when it
     * has none or its day, origin or vehicle changed. A manual price always wins.
     */
    public SaveResult<Trip> save(Trip trip, PriceQuote manualPrice) {
        return save(trip, manualPrice, true).result();
    }

    /** A save, and why Google gave no route, to tell a missing key or network from a route it could not find. */
    private record Outcome(SaveResult<Trip> result, ExternalApiException routeFailure) {
    }

    private Outcome save(Trip trip, PriceQuote manualPrice, boolean askGoogle) {
        Vehicle vehicle = vehicles.findById(trip.vehicleId())
                .orElseThrow(() -> new ValidationException("Choose a vehicle"));
        drivers.findById(trip.driverId()).orElseThrow(() -> new ValidationException("Choose a driver"));
        if (trip.originPlaceId() == null || trip.destinationPlaceId() == null) {
            throw new ValidationException("Choose the origin and the destination");
        }
        if (trip.startOdometerKm() < vehicle.initialOdometerKm()) {
            throw new ValidationException("The odometer cannot be below the vehicle's initial "
                    + vehicle.initialOdometerKm() + " km");
        }
        if (trip.endOdometerKm() != null && trip.endOdometerKm() < trip.startOdometerKm()) {
            throw new ValidationException("The end odometer cannot be below the start odometer");
        }
        Place origin = places.findById(trip.originPlaceId()).orElseThrow();
        Place destination = places.findById(trip.destinationPlaceId()).orElseThrow();
        Trip stored = trip.id() == null ? null : trips.findById(trip.id()).orElse(null);
        List<String> warnings = new ArrayList<>();
        ExternalApiException routeFailure = null;

        Trip result = trip;
        boolean placesChanged = stored == null
                || !Objects.equals(stored.originPlaceId(), trip.originPlaceId())
                || !Objects.equals(stored.destinationPlaceId(), trip.destinationPlaceId());
        RouteEstimate route = placesChanged || stored.route() == null ? null : stored.route();
        if (route == null && askGoogle) {
            try {
                route = routes.route(origin, destination, vehicle.fuelType());
            } catch (ExternalApiException e) {
                routeFailure = e;
                warnings.add("Google route not calculated: " + e.getMessage());
            }
        }
        result = result.withRoute(route);

        if (manualPrice != null) {
            result = result.withPrice(manualPrice);
        } else {
            boolean priceInputsChanged = stored == null || stored.price() == null || stored.price().isManual()
                    || !stored.date().equals(trip.date())
                    || !Objects.equals(stored.originPlaceId(), trip.originPlaceId())
                    || stored.vehicleId() != trip.vehicleId();
            PriceQuote price = priceInputsChanged ? null : stored.price();
            if (price == null) {
                price = prices.quote(origin, trip.date(), vehicle.fuelType()).orElse(null);
                if (price == null) {
                    warnings.add("No fuel price for " + trip.date() + ": type one in the trip or in Fuel prices");
                }
            }
            result = result.withPrice(price);
        }
        Trip saved = trips.save(result);
        places.markUsed(origin.id());
        places.markUsed(destination.id());
        return new Outcome(new SaveResult<>(saved, warnings), routeFailure);
    }

    /** What {@link #completePending()} managed. {@code problems}: the first message of each kind. */
    public record PendingResult(int trips, int routesAdded, int pricesAdded, List<String> problems,
                                boolean googleStopped) {
    }

    /** Trips with their origin and destination but no Google route or no fuel price. */
    public List<Trip> pending() {
        return trips.findAll().stream()
                .filter(t -> t.originPlaceId() != null && t.destinationPlaceId() != null)
                .filter(t -> t.route() == null || t.price() == null)
                .toList();
    }

    /**
     * Asks Google and the price service for every pending trip, keeping what each one already has (and a manual
     * price). When Google does not answer at all, or refuses the key, it is not asked again for the rest: prices
     * are still completed.
     */
    public PendingResult completePending() {
        List<Trip> pending = pending();
        int routesAdded = 0;
        int pricesAdded = 0;
        boolean askGoogle = true;
        Set<String> problems = new LinkedHashSet<>();
        for (Trip trip : pending) {
            if (!askGoogle && trip.price() != null) {
                continue;
            }
            Outcome outcome;
            try {
                outcome = save(trip, trip.price() != null && trip.price().isManual() ? trip.price() : null,
                        askGoogle);
            } catch (ValidationException e) {
                problems.add(Formats.date(trip.date()) + ": " + e.getMessage());
                continue;
            }
            Trip saved = outcome.result().saved();
            routesAdded += trip.route() == null && saved.route() != null ? 1 : 0;
            pricesAdded += trip.price() == null && saved.price() != null ? 1 : 0;
            outcome.result().warnings().forEach(warning -> problems.add(warning.replaceAll("\\d{4}-\\d{2}-\\d{2}",
                    "the trip's day")));
            ExternalApiException failure = outcome.routeFailure();
            if (failure != null && (failure.status() == 0 || failure.status() == 401 || failure.status() == 403)) {
                askGoogle = false;
            }
        }
        return new PendingResult(pending.size(), routesAdded, pricesAdded, List.copyOf(problems), !askGoogle);
    }

    /** Asks Google and the price service again, keeping a manual price. */
    public SaveResult<Trip> recalculate(long tripId) {
        Trip trip = trips.findById(tripId).orElseThrow(() -> new ValidationException("The trip no longer exists"));
        Trip cleared = trips.save(trip.withRoute(null).withPrice(trip.price() != null && trip.price().isManual()
                ? trip.price() : null));
        return save(cleared, cleared.price());
    }

    public void delete(long tripId) {
        trips.delete(tripId);
    }

    public Optional<Trip> findById(long tripId) {
        return trips.findById(tripId);
    }

    /**
     * The odometer to propose for a new trip: the highest reading known before that moment ({@link
     * OdometerEstimator}: trips' starts and noted ends, and refuels whose odometer was read).
     */
    public int suggestedStartOdometer(long vehicleId, LocalDateTime startAt) {
        return vehicles.findById(vehicleId).map(vehicle -> OdometerEstimator.estimate(vehicle,
                trips.findByVehicle(vehicleId), refuels.findAll().stream()
                        .filter(r -> r.vehicleId() == vehicleId).toList(), startAt.minusNanos(1))).orElse(0);
    }

    /** Every trip with its odometer distance and divergences, most recent first. */
    public List<TripRow> rows() {
        FleetAnalysis fleet = analysis.analyse();
        Map<Long, Vehicle> vehiclesById = byId(vehicles.findAll(), Vehicle::id);
        Map<Long, Driver> driversById = byId(drivers.findAll(), Driver::id);
        Map<Long, Place> placesById = byId(places.findAll(), Place::id);
        return trips.findAll().stream()
                .sorted(Comparator.comparing(Trip::startAt).reversed())
                .map(trip -> {
                    FleetAnalysis.TripAnalysis analysed = fleet.trip(trip.id());
                    return new TripRow(trip, vehiclesById.get(trip.vehicleId()), driversById.get(trip.driverId()),
                            placesById.get(trip.originPlaceId()), placesById.get(trip.destinationPlaceId()),
                            analysed.odometerKm(), analysed.unrecordedKm(), analysed.pending(),
                            analysed.divergences());
                })
                .toList();
    }

    static <T> Map<Long, T> byId(List<T> items, Function<T, Long> id) {
        return items.stream().collect(Collectors.toMap(id, item -> item));
    }
}
