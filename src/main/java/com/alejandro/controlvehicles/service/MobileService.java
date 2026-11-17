package com.alejandro.controlvehicles.service;

import com.alejandro.controlvehicles.domain.Driver;
import com.alejandro.controlvehicles.domain.Geo;
import com.alejandro.controlvehicles.domain.OdometerEstimator;
import com.alejandro.controlvehicles.domain.Place;
import com.alejandro.controlvehicles.domain.Trip;
import com.alejandro.controlvehicles.domain.Vehicle;
import com.alejandro.controlvehicles.integration.ExternalApiException;
import com.alejandro.controlvehicles.integration.google.PlacesClient;
import com.alejandro.controlvehicles.integration.mobile.MobileClient;
import com.alejandro.controlvehicles.integration.mobile.MobileTrip;
import com.alejandro.controlvehicles.persistence.DriverRepository;
import com.alejandro.controlvehicles.persistence.PlaceRepository;
import com.alejandro.controlvehicles.persistence.RefuelRepository;
import com.alejandro.controlvehicles.persistence.TripRepository;
import com.alejandro.controlvehicles.persistence.VehicleRepository;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The drivers' phone app (repository control-vehicles-mobile): the desktop publishes its drivers and vehicles to
 * the app's Google Sheet, gives each driver a personal link, and pulls the finished trips as normal trips.
 */
public final class MobileService {

    /** A stored place this close to a GPS point is taken as that point (the office, a usual client). */
    static final double SAME_PLACE_KM = 0.25;
    static final String NO_GPS = "No GPS location";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** What a pull did. {@code waiting}: trips still in progress on a phone. */
    public record PullResult(int imported, int waiting, int cancelled, List<String> skipped, List<String> warnings) {
    }

    private final MobileClient client;
    private final Supplier<MobileSettings> settings;
    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private final TripRepository trips;
    private final RefuelRepository refuels;
    private final PlaceRepository places;
    private final PlacesClient google;
    private final TripService tripService;
    private final Clock clock;

    public MobileService(MobileClient client, Supplier<MobileSettings> settings, DriverRepository drivers,
                         VehicleRepository vehicles, TripRepository trips, RefuelRepository refuels,
                         PlaceRepository places, PlacesClient google, TripService tripService, Clock clock) {
        this.client = client;
        this.settings = settings;
        this.drivers = drivers;
        this.vehicles = vehicles;
        this.trips = trips;
        this.refuels = refuels;
        this.places = places;
        this.google = google;
        this.tripService = tripService;
        this.clock = clock;
    }

    /**
     * Sends the active drivers (each with the token of their link, created the first time) and the active vehicles
     * with their last known odometer, which the phone shows as a hint. Returns how many of each.
     */
    public int[] publish() {
        List<MobileClient.PublishedDriver> publishedDrivers = drivers.findAll().stream().filter(Driver::active)
                .map(d -> new MobileClient.PublishedDriver(token(d.id()), d.id(), d.name())).toList();
        LocalDateTime now = LocalDateTime.now(clock);
        List<MobileClient.PublishedVehicle> publishedVehicles = vehicles.findAll().stream().filter(Vehicle::active)
                .map(v -> new MobileClient.PublishedVehicle(v.id(), v.plate(), v.label(),
                        OdometerEstimator.estimate(v, trips.findByVehicle(v.id()), refuels.findAll().stream()
                                .filter(r -> r.vehicleId() == v.id()).toList(), now)))
                .toList();
        client.publish(publishedDrivers, publishedVehicles);
        return new int[]{publishedDrivers.size(), publishedVehicles.size()};
    }

    /** How many trips the Sheet holds that have not been brought yet (also a check of the connection). */
    public int pending() {
        return client.export().size();
    }

    /** The driver's personal link: the app's address with the web app and the driver's token. */
    public String link(long driverId) {
        MobileSettings s = settings.get();
        if (s.apiUrl().isEmpty()) {
            throw new ValidationException("Set the web app URL in Settings → Mobile first");
        }
        String app = s.appUrl().endsWith("/") ? s.appUrl() : s.appUrl() + "/";
        return app + "?api=" + URLEncoder.encode(s.apiUrl(), StandardCharsets.UTF_8) + "&d=" + token(driverId);
    }

    /** A new token for the driver, published at once: the old link stops working. */
    public String newLink(long driverId) {
        drivers.setMobileToken(driverId, newToken());
        publish();
        return link(driverId);
    }

    /**
     * Brings the finished trips: each becomes a normal trip (Google route, fuel price, divergences), with the GPS
     * points as origin and destination. Imported and cancelled ones are acknowledged so they are not sent again; one
     * whose vehicle or driver is unknown here is left on the Sheet and told, to be fixed and pulled again.
     */
    public PullResult pull() {
        List<MobileTrip> exported = client.export();
        Set<String> already = trips.mobileIds();
        List<String> ack = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int imported = 0;
        int waiting = 0;
        int cancelled = 0;
        for (MobileTrip mobile : exported.stream()
                .sorted(Comparator.comparing(MobileTrip::startAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList()) {
            switch (mobile.status()) {
                case OPEN -> waiting++;
                case CANCELLED -> {
                    cancelled++;
                    ack.add(mobile.id());
                }
                case CLOSED -> {
                    if (already.contains(mobile.id())) {
                        ack.add(mobile.id());
                        continue;
                    }
                    String who = describe(mobile);
                    try {
                        SaveResult<Trip> saved = importTrip(mobile);
                        trips.setMobileId(saved.saved().id(), mobile.id());
                        already.add(mobile.id());
                        ack.add(mobile.id());
                        imported++;
                        saved.warnings().forEach(w -> warnings.add(who + ": " + w));
                    } catch (ValidationException | IllegalArgumentException e) {
                        skipped.add(who + ": " + e.getMessage());
                    }
                }
            }
        }
        client.ack(ack);
        return new PullResult(imported, waiting, cancelled, skipped, warnings);
    }

    private SaveResult<Trip> importTrip(MobileTrip mobile) {
        Vehicle vehicle = mobile.vehicleId() == null ? null : vehicles.findById(mobile.vehicleId()).orElse(null);
        if (vehicle == null) {
            throw new ValidationException("vehicle " + mobile.plate() + " is not in this application");
        }
        Driver driver = mobile.driverId() == null ? null : drivers.findById(mobile.driverId()).orElse(null);
        if (driver == null) {
            throw new ValidationException("driver " + mobile.driverName() + " is not in this application");
        }
        if (mobile.startAt() == null || mobile.endAt() == null || mobile.startKm() == null) {
            throw new ValidationException("incomplete on the phone (start, end or odometer missing)");
        }
        List<String> notes = new ArrayList<>();
        notes.add("From mobile");
        Integer endKm = mobile.endKm();
        if (endKm != null && endKm < mobile.startKm()) {
            notes.add("end odometer " + endKm + " below the start, ignored");
            endKm = null;
        }
        Place origin = place(mobile.startLat(), mobile.startLng());
        Place destination = place(mobile.endLat(), mobile.endLng());
        LocalDateTime start = mobile.startAt().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime end = mobile.endAt().truncatedTo(ChronoUnit.MINUTES);
        Trip trip = new Trip(null, vehicle.id(), driver.id(), start.toLocalDate(), start.toLocalTime(),
                end.toLocalTime(), origin.id(), destination.id(), mobile.startKm(), endKm,
                mobile.tolls() == null ? BigDecimal.ZERO : mobile.tolls(), String.join(" · ", notes), null, null);
        return tripService.save(trip, null);
    }

    /**
     * The place of a GPS point: a stored place within {@link #SAME_PLACE_KM}, else a new one named by Google, else
     * named by its coordinates; without a fix, the shared «No GPS location» place (no route can be computed).
     */
    private Place place(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return places.findAll().stream().filter(p -> NO_GPS.equals(p.name()) && !p.hasLocation()).findFirst()
                    .orElseGet(() -> places.save(new Place(null, null, NO_GPS, null, null, null, null)));
        }
        Optional<Place> near = places.findAll().stream().filter(Place::hasLocation)
                .filter(p -> Geo.distanceKm(p.latitude(), p.longitude(), latitude, longitude) <= SAME_PLACE_KM)
                .min(Comparator.comparingDouble(p -> Geo.distanceKm(p.latitude(), p.longitude(), latitude,
                        longitude)));
        if (near.isPresent()) {
            return near.get();
        }
        Place named;
        try {
            named = google.nearest(latitude, longitude).orElse(null);
        } catch (ExternalApiException e) {
            named = null;
        }
        if (named == null) {
            named = new Place(null, null, String.format(Locale.ENGLISH, "GPS %.5f, %.5f", latitude, longitude), null,
                    latitude, longitude, null);
        }
        return places.save(named);
    }

    private String token(long driverId) {
        return drivers.mobileToken(driverId).orElseGet(() -> {
            String token = newToken();
            drivers.setMobileToken(driverId, token);
            return token;
        });
    }

    private static String newToken() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String describe(MobileTrip mobile) {
        return ((mobile.startAt() == null ? "?" : mobile.startAt().toString().replace('T', ' ')) + " "
                + (mobile.plate() == null ? "" : mobile.plate()) + " " + (mobile.driverName() == null ? ""
                : mobile.driverName())).trim();
    }
}
