package com.alejandro.controlvehicles.integration.google;

import com.alejandro.controlvehicles.domain.FuelType;
import com.alejandro.controlvehicles.domain.Place;
import com.alejandro.controlvehicles.domain.RouteEstimate;
import com.alejandro.controlvehicles.domain.TollStatus;
import com.alejandro.controlvehicles.integration.ExternalApiException;
import com.alejandro.controlvehicles.integration.HttpJson;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Routes API {@code computeRoutes}: the distance and driving time between two places without traffic (a trip is
 * registered after it happened, so the traffic of that moment is unknown), plus the estimated tolls.
 */
public final class RoutesClient {

    private static final String FIELDS = "routes.distanceMeters,routes.duration,routes.travelAdvisory.tollInfo";

    private final Supplier<GoogleSettings> settingsSource;
    private final HttpJson http;
    private final Clock clock;

    public RoutesClient(Supplier<GoogleSettings> settingsSource, HttpJson http, Clock clock) {
        this.settingsSource = settingsSource;
        this.http = http;
        this.clock = clock;
    }

    public RouteEstimate route(Place origin, Place destination, FuelType fuelType) {
        GoogleSettings settings = settingsSource.get();
        if (!settings.configured()) {
            throw new ExternalApiException("No Google API key: set it in Settings", 0);
        }
        try {
            return compute(settings, origin, destination, fuelType, true);
        } catch (ExternalApiException e) {
            if (e.status() != 400) {
                throw e;
            }
            // Si Google no admite el cálculo de peajes para esa petición, la ruta sigue valiendo sin ellos.
            return compute(settings, origin, destination, fuelType, false);
        }
    }

    private RouteEstimate compute(GoogleSettings settings, Place origin, Place destination, FuelType fuelType,
                                  boolean withTolls) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("origin", waypoint(origin));
        body.put("destination", waypoint(destination));
        body.put("travelMode", "DRIVE");
        body.put("routingPreference", "TRAFFIC_UNAWARE");
        body.put("languageCode", settings.languageCode());
        if (withTolls) {
            body.put("extraComputations", List.of("TOLLS"));
            body.put("routeModifiers", Map.of("vehicleInfo", Map.of("emissionType", fuelType.name())));
        }
        JsonNode response = http.post(settings.routesBaseUrl() + "/directions/v2:computeRoutes",
                Map.of("X-Goog-Api-Key", settings.apiKey(), "X-Goog-FieldMask", FIELDS), body);
        JsonNode route = response.path("routes").path(0);
        if (route.isMissingNode()) {
            throw new ExternalApiException("Google found no driving route between those places", 200);
        }
        int meters = route.path("distanceMeters").asInt(0);
        int seconds = parseSeconds(route.path("duration").asText("0s"));
        TollStatus status = TollStatus.UNPRICED;
        BigDecimal estimate = null;
        if (withTolls) {
            JsonNode tollInfo = route.path("travelAdvisory").path("tollInfo");
            if (tollInfo.isMissingNode()) {
                status = TollStatus.NONE;
            } else {
                estimate = euros(tollInfo.path("estimatedPrice"));
                status = estimate == null ? TollStatus.UNPRICED : TollStatus.PRICED;
            }
        }
        return new RouteEstimate(meters, seconds, status, estimate, clock.instant());
    }

    private static Map<String, Object> waypoint(Place place) {
        if (place.googlePlaceId() != null) {
            return Map.of("placeId", place.googlePlaceId());
        }
        if (place.hasLocation()) {
            return Map.of("location", Map.of("latLng", Map.of("latitude", place.latitude(),
                    "longitude", place.longitude())));
        }
        return Map.of("address", place.label());
    }

    /** "1234s" or "1234.5s". */
    static int parseSeconds(String duration) {
        String number = duration.endsWith("s") ? duration.substring(0, duration.length() - 1) : duration;
        return (int) Math.round(Double.parseDouble(number.isBlank() ? "0" : number));
    }

    /** The sum of the EUR {@code Money} entries, or {@code null} if there is none. */
    private static BigDecimal euros(JsonNode prices) {
        BigDecimal total = null;
        for (JsonNode money : prices) {
            if (!"EUR".equals(money.path("currencyCode").asText())) {
                continue;
            }
            BigDecimal value = new BigDecimal(money.path("units").asText("0"))
                    .add(BigDecimal.valueOf(money.path("nanos").asLong(0), 9));
            total = total == null ? value : total.add(value);
        }
        return total == null ? null : total.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
