package com.alejandro.controlvehicles.integration.mobile;

import com.alejandro.controlvehicles.integration.ExternalApiException;
import com.alejandro.controlvehicles.integration.HttpJson;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The desktop's side of the phone app's backend, the Apps Script web app over a Google Sheet (CONTRACT.md of
 * control-vehicles-mobile): publish the drivers and vehicles, export the trips not yet imported, acknowledge them.
 * Apps Script answers through a redirect, which {@link HttpJson} follows, and always with HTTP 200: a refusal is
 * {@code {"ok": false, "error": ...}}, turned into an {@link ExternalApiException}.
 */
public final class MobileClient {

    /** The address of the web app and its admin secret. */
    public record Connection(String apiUrl, String secret) {

        public boolean configured() {
            return apiUrl != null && !apiUrl.isBlank() && secret != null && !secret.isBlank();
        }
    }

    public record PublishedDriver(String token, long driverId, String name) {
    }

    public record PublishedVehicle(long vehicleId, String plate, String label, Integer lastKm) {
    }

    private final Supplier<Connection> connection;
    private final HttpJson http;

    public MobileClient(Supplier<Connection> connection, HttpJson http) {
        this.connection = connection;
        this.http = http;
    }

    public void publish(List<PublishedDriver> drivers, List<PublishedVehicle> vehicles) {
        Connection c = require();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("action", "publish");
        body.put("secret", c.secret());
        body.put("drivers", drivers.stream().map(d -> Map.of("token", d.token(), "driverId", d.driverId(),
                "name", d.name())).toList());
        body.put("vehicles", vehicles.stream().map(v -> {
            Map<String, Object> vehicle = new LinkedHashMap<>();
            vehicle.put("vehicleId", v.vehicleId());
            vehicle.put("plate", v.plate());
            vehicle.put("label", v.label());
            vehicle.put("lastKm", v.lastKm());
            return vehicle;
        }).toList());
        checked(http.post(c.apiUrl(), Map.of(), body));
    }

    public List<MobileTrip> export() {
        Connection c = require();
        String url = c.apiUrl() + (c.apiUrl().contains("?") ? "&" : "?") + "action=export&secret="
                + URLEncoder.encode(c.secret(), StandardCharsets.UTF_8);
        JsonNode answer = checked(http.get(url, Map.of()));
        List<MobileTrip> trips = new ArrayList<>();
        for (JsonNode row : answer.path("trips")) {
            trips.add(trip(row));
        }
        return trips;
    }

    public void ack(List<String> ids) {
        if (ids.isEmpty()) {
            return;
        }
        Connection c = require();
        checked(http.post(c.apiUrl(), Map.of(), Map.of("action", "ack", "secret", c.secret(), "ids", ids)));
    }

    private Connection require() {
        Connection c = connection.get();
        if (!c.configured()) {
            throw new ExternalApiException("The phone app is not connected: set its web app URL and secret in"
                    + " Settings → Mobile", 0);
        }
        return c;
    }

    private static JsonNode checked(JsonNode answer) {
        if (!answer.path("ok").asBoolean(false)) {
            String error = answer.path("error").asText("no answer");
            throw new ExternalApiException("The phone app's backend refused it: " + error
                    + ("Wrong admin secret".equals(error) ? " (check it in Settings → Mobile)" : ""), 200);
        }
        return answer;
    }

    /** Every field comes as text from the Sheet; an empty one is absent. */
    static MobileTrip trip(JsonNode row) {
        String status = text(row, "status");
        return new MobileTrip(text(row, "id"), longOf(row, "driverId"), text(row, "driverName"),
                longOf(row, "vehicleId"), text(row, "plate"), dateTime(row, "startAt"), doubleOf(row, "startLat"),
                doubleOf(row, "startLng"), intOf(row, "startAccuracy"), intOf(row, "startKm"), dateTime(row, "endAt"),
                doubleOf(row, "endLat"), doubleOf(row, "endLng"), intOf(row, "endAccuracy"), intOf(row, "endKm"),
                decimal(row, "tolls"), status == null ? MobileTrip.Status.OPEN : MobileTrip.Status.valueOf(status));
    }

    private static String text(JsonNode row, String field) {
        JsonNode node = row.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText().trim();
        return value.isEmpty() || value.equals("null") ? null : value;
    }

    private static Long longOf(JsonNode row, String field) {
        String value = text(row, field);
        return value == null ? null : new BigDecimal(value).longValue();
    }

    private static Integer intOf(JsonNode row, String field) {
        String value = text(row, field);
        return value == null ? null : new BigDecimal(value).intValue();
    }

    private static Double doubleOf(JsonNode row, String field) {
        String value = text(row, field);
        return value == null ? null : Double.valueOf(value);
    }

    private static BigDecimal decimal(JsonNode row, String field) {
        String value = text(row, field);
        return value == null ? null : new BigDecimal(value.replace(',', '.'));
    }

    private static LocalDateTime dateTime(JsonNode row, String field) {
        String value = text(row, field);
        return value == null ? null : LocalDateTime.parse(value.length() > 16 ? value.substring(0, 16) : value);
    }
}
