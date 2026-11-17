package com.alejandro.controlvehicles;

import com.alejandro.controlvehicles.domain.Country;
import com.alejandro.controlvehicles.domain.FuelType;
import com.alejandro.controlvehicles.domain.Place;
import com.alejandro.controlvehicles.domain.RouteEstimate;
import com.alejandro.controlvehicles.domain.TollStatus;
import com.alejandro.controlvehicles.integration.ExternalApiException;
import com.alejandro.controlvehicles.integration.HttpJson;
import com.alejandro.controlvehicles.integration.fuel.PortugalDgegProvider;
import com.alejandro.controlvehicles.integration.fuel.SpainMitecoProvider;
import com.alejandro.controlvehicles.integration.fuel.StationPrice;
import com.alejandro.controlvehicles.integration.google.GoogleSettings;
import com.alejandro.controlvehicles.integration.google.PlaceSuggestion;
import com.alejandro.controlvehicles.integration.google.PlacesClient;
import com.alejandro.controlvehicles.integration.google.RoutesClient;
import com.alejandro.controlvehicles.integration.mobile.MobileClient;
import com.alejandro.controlvehicles.integration.mobile.MobileTrip;
import com.alejandro.controlvehicles.support.FakeHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientsTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"), ZoneId.of("Europe/Madrid"));

    private final FakeHttpServer server = new FakeHttpServer();
    private final HttpJson http = new HttpJson(Duration.ofSeconds(5));
    private final GoogleSettings google = new GoogleSettings("test-key", server.baseUrl(), server.baseUrl(), "en");

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void autocompleteSendsTheKeyTheSessionAndOnlySpainAndPortugal() {
        server.onFixture("/v1/places:autocomplete", "places-autocomplete.json");
        List<PlaceSuggestion> suggestions = new PlacesClient(() -> google, http).autocomplete("madr", "session-1");

        assertEquals(2, suggestions.size(), "query predictions are not places");
        assertEquals("Madrid, Spain", suggestions.getFirst().label());
        FakeHttpServer.Request request = server.requests().getFirst();
        assertEquals("POST", request.method());
        assertEquals("test-key", request.header("X-Goog-Api-Key"));
        assertTrue(request.body().contains("\"sessionToken\":\"session-1\""));
        assertTrue(request.body().contains("\"includedRegionCodes\":[\"es\",\"pt\"]"));
    }

    @Test
    void placeDetailsBringTheLocationAndTheCountry() {
        server.onFixture("/v1/places/", "places-details-madrid.json");
        Place place = new PlacesClient(() -> google, http).details("ChIJgTwKgJcpQg0RaSKMYcHeNsQ", "s");

        assertEquals("Madrid", place.name());
        assertEquals(Country.ES, place.country());
        assertEquals(40.4167754, place.latitude());
        FakeHttpServer.Request request = server.requests().getFirst();
        assertTrue(request.header("X-Goog-FieldMask").contains("addressComponents"));
        assertTrue(request.query().contains("sessionToken=s"));
    }

    @Test
    void withoutAKeyNothingIsCalled() {
        GoogleSettings none = GoogleSettings.of("");
        assertThrows(ExternalApiException.class, () -> new PlacesClient(() -> none, http).autocomplete("x", "s"));
        assertTrue(server.requests().isEmpty());
    }

    @Test
    void aRouteBringsDistanceDurationAndTollsInEuros() {
        server.onFixture("/directions/v2:computeRoutes", "routes-with-tolls.json");
        RouteEstimate route = routes().route(place("a"), place("b"), FuelType.DIESEL);

        assertEquals(72_450, route.distanceMeters());
        assertEquals(3960, route.durationSeconds());
        assertEquals(TollStatus.PRICED, route.tollStatus());
        assertEquals(new BigDecimal("7.45"), route.tollEstimate());
        assertEquals(Instant.parse("2026-10-30T10:00:00Z"), route.computedAt());
        FakeHttpServer.Request request = server.requests().getFirst();
        assertTrue(request.header("X-Goog-FieldMask").contains("routes.travelAdvisory.tollInfo"));
        assertTrue(request.body().contains("\"placeId\":\"a\""));
        assertTrue(request.body().contains("\"emissionType\":\"DIESEL\""));
        assertTrue(request.body().contains("TRAFFIC_UNAWARE"));
    }

    @Test
    void aRouteWithoutTollInfoHasNoTolls() {
        server.onFixture("/directions/v2:computeRoutes", "routes-no-tolls.json");
        RouteEstimate route = routes().route(place("a"), place("b"), FuelType.GASOLINE);
        assertEquals(TollStatus.NONE, route.tollStatus());
        assertEquals(4013, route.durationSeconds());
    }

    @Test
    void ifGoogleRefusesTheTollsTheRouteIsAskedWithoutThem() {
        String withoutTolls = FakeHttpServer.fixture("routes-no-tolls.json");
        server.on("/directions/v2:computeRoutes", request -> request.body().contains("TOLLS")
                ? new FakeHttpServer.Response(400, "{\"error\":{\"message\":\"Tolls not supported\"}}")
                : new FakeHttpServer.Response(200, withoutTolls));
        RouteEstimate route = routes().route(place("a"), place("b"), FuelType.GASOLINE);
        assertEquals(TollStatus.UNPRICED, route.tollStatus());
        assertEquals(2, server.requests().size());
    }

    @Test
    void googleErrorsCarryItsMessage() {
        server.on("/directions/v2:computeRoutes", request -> new FakeHttpServer.Response(403,
                "{\"error\":{\"code\":403,\"message\":\"Routes API has not been used in project\"}}"));
        ExternalApiException e = assertThrows(ExternalApiException.class,
                () -> routes().route(place("a"), place("b"), FuelType.GASOLINE));
        assertEquals(403, e.status());
        assertTrue(e.getMessage().contains("Routes API has not been used"));
    }

    @Test
    void aDisabledApiSaysWhichOneAndWhereToEnableIt() {
        server.on("/v1/places:autocomplete", r -> refusal(403, "SERVICE_DISABLED", "places.googleapis.com"));
        ExternalApiException e = assertThrows(ExternalApiException.class,
                () -> new PlacesClient(() -> google, http).autocomplete("madr", "s"));
        assertEquals("SERVICE_DISABLED", e.reason());
        assertTrue(e.getMessage().startsWith("Places API (New) is not enabled in the Google Cloud project"),
                e.getMessage());
    }

    @Test
    void anApiMissingFromTheKeyRestrictionsIsNamed() {
        server.on("/directions/v2:computeRoutes", r -> refusal(403, "API_KEY_SERVICE_BLOCKED",
                "routes.googleapis.com"));
        ExternalApiException e = assertThrows(ExternalApiException.class,
                () -> routes().route(place("a"), place("b"), FuelType.DIESEL));
        assertTrue(e.getMessage().startsWith("The key's API restrictions do not include Routes API"),
                e.getMessage());
    }

    @Test
    void billingAndApplicationRestrictionsAreExplained() {
        server.on("/v1/places/", r -> refusal(403, "BILLING_DISABLED", "places.googleapis.com"));
        assertTrue(assertThrows(ExternalApiException.class, () -> new PlacesClient(() -> google, http)
                .details("x", "s")).getMessage().startsWith("Billing is not enabled"));
        server.on("/v1/places:autocomplete", r -> refusal(403, "API_KEY_HTTP_REFERRER_BLOCKED",
                "places.googleapis.com"));
        assertTrue(assertThrows(ExternalApiException.class, () -> new PlacesClient(() -> google, http)
                .autocomplete("madr", "s")).getMessage().contains("set Application restrictions to None"));
    }

    @Test
    void anInvalidKeyIsAskedOnceAndSaidSo() {
        server.on("/directions/v2:computeRoutes", r -> refusal(400, "API_KEY_INVALID", "routes.googleapis.com"));
        ExternalApiException e = assertThrows(ExternalApiException.class,
                () -> routes().route(place("a"), place("b"), FuelType.DIESEL));
        assertEquals("The key is not valid: copy it again from Credentials → Show key.", e.getMessage());
        assertEquals(1, server.requests().size(), "not retried without tolls");
    }

    @Test
    void aRefusalWithoutReasonGetsTheChecklist() {
        server.on("/v1/places:autocomplete", r -> new FakeHttpServer.Response(403,
                "{\"error\":{\"code\":403,\"message\":\"The caller does not have permission\","
                        + "\"status\":\"PERMISSION_DENIED\"}}"));
        ExternalApiException e = assertThrows(ExternalApiException.class,
                () -> new PlacesClient(() -> google, http).autocomplete("madr", "s"));
        assertTrue(e.getMessage().contains("The caller does not have permission"), e.getMessage());
        assertTrue(e.getMessage().contains("Places API (New) and Routes API are enabled"), e.getMessage());
    }

    @Test
    void theMobileExportIsReadFieldByFieldFromTheSheetsText() {
        server.onFixture("/exec", "mobile-export.json");
        List<MobileTrip> trips = mobile("s3cret").export();

        assertEquals(4, trips.size());
        MobileTrip closed = trips.getFirst();
        assertEquals("trip-closed", closed.id());
        assertEquals(MobileTrip.Status.CLOSED, closed.status());
        assertEquals(LocalDateTime.of(2026, 10, 28, 8, 15), closed.startAt());
        assertEquals(40.4169, closed.startLat());
        assertEquals(100_074, closed.endKm());
        assertEquals(new BigDecimal("3.10"), closed.tolls());
        MobileTrip open = trips.get(1);
        assertEquals(MobileTrip.Status.OPEN, open.status());
        assertNull(open.endAt());
        assertNull(open.endKm());
        assertFalse(trips.get(2).hasStartPosition());
        assertEquals("action=export&secret=s3cret", server.requests().getFirst().query());
    }

    @Test
    void publishingAndAcknowledgingFollowAppsScriptsRedirect() {
        server.on("/exec", request -> FakeHttpServer.Response.redirect("/echo?user_content_key=abc"));
        server.on("/echo", request -> new FakeHttpServer.Response(200, "{\"ok\":true,\"drivers\":1,\"vehicles\":1}"));
        MobileClient client = mobile("s3cret");

        client.publish(List.of(new MobileClient.PublishedDriver("tok", 1, "Ana")),
                List.of(new MobileClient.PublishedVehicle(7, "1234 KLM", "1234 KLM · Kangoo", 120_423)));
        client.ack(List.of("trip-1", "trip-2"));
        client.ack(List.of());

        List<FakeHttpServer.Request> posts = server.requests("/exec");
        assertEquals(2, posts.size(), "an empty ack sends nothing");
        assertTrue(posts.get(0).body().contains("\"action\":\"publish\""));
        assertTrue(posts.get(0).body().contains("\"token\":\"tok\""));
        assertTrue(posts.get(0).body().contains("\"lastKm\":120423"));
        assertTrue(posts.get(1).body().contains("\"ids\":[\"trip-1\",\"trip-2\"]"));
        List<FakeHttpServer.Request> followed = server.requests("/echo");
        assertEquals(2, followed.size());
        assertEquals("GET", followed.getFirst().method(), "Apps Script's answer is fetched with a GET");
    }

    @Test
    void aRefusalOfTheBackendSaysWhy() {
        server.on("/exec", request -> new FakeHttpServer.Response(200,
                "{\"ok\":false,\"error\":\"Wrong admin secret\"}"));
        ExternalApiException e = assertThrows(ExternalApiException.class, () -> mobile("bad").export());
        assertTrue(e.getMessage().contains("Wrong admin secret"), e.getMessage());
        assertTrue(e.getMessage().contains("Settings → Mobile"));
    }

    @Test
    void withoutTheMobileConnectionNothingIsCalled() {
        MobileClient client = new MobileClient(() -> new MobileClient.Connection("", ""), http);
        assertThrows(ExternalApiException.class, client::export);
        assertTrue(server.requests().isEmpty());
    }

    @Test
    void aGpsPointIsNamedByTheNearestPlaceGoogleKnowsKeepingItsCoordinates() {
        server.onFixture("/v1/places:searchNearby", "places-nearby-toledo.json");
        Place place = new PlacesClient(() -> google, http).nearest(39.86, -4.02).orElseThrow();

        assertEquals("Toledo", place.name());
        assertEquals("Av. de Europa, 1, 45005 Toledo, Spain", place.address());
        assertEquals(Country.ES, place.country());
        assertEquals(39.86, place.latitude());
        assertNull(place.googlePlaceId(), "the route goes from where the phone was, not from the place");
        String body = server.requests().getFirst().body();
        assertTrue(body.contains("\"rankPreference\":\"DISTANCE\""));
        assertTrue(body.contains("\"radius\":150.0"));

        server.on("/v1/places:searchNearby", request -> new FakeHttpServer.Response(200, "{}"));
        assertTrue(new PlacesClient(() -> google, http).nearest(0, 0).isEmpty());
    }

    @Test
    void mitecoPricesAreParsedWithDecimalCommasAndOnlyWhenThereIsOne() {
        server.onFixture("/EstacionesTerrestres", "miteco-stations.json");
        SpainMitecoProvider provider = new SpainMitecoProvider(server.baseUrl(), http, CLOCK);

        List<StationPrice> gasoline = provider.stationPrices(LocalDate.of(2026, 10, 30), FuelType.GASOLINE);
        List<StationPrice> diesel = provider.stationPrices(LocalDate.of(2026, 10, 30), FuelType.DIESEL);

        assertEquals(3, gasoline.size(), "Repsol has no gasoline price and one station has no coordinates");
        assertEquals(4, diesel.size());
        StationPrice first = diesel.getFirst();
        assertEquals(new BigDecimal("1.459"), first.price());
        assertEquals(40.4271, first.latitude(), 1e-9);
        assertTrue(first.isBrand("galp"));
        assertEquals(1, server.requests().size(), "one answer per day serves both fuels");
        assertEquals("/EstacionesTerrestres/", server.requests().getFirst().path());
    }

    @Test
    void aPastDayAsksMitecoForItsHistory() {
        server.onFixture("/EstacionesTerrestresHist/", "miteco-stations.json");
        new SpainMitecoProvider(server.baseUrl(), http, CLOCK).stationPrices(LocalDate.of(2026, 9, 3),
                FuelType.DIESEL);
        assertEquals("/EstacionesTerrestresHist/03-09-2026", server.requests().getFirst().path());
    }

    @Test
    void dgegPricesComeWithTheEuroSignAndNumbersOrTextCoordinates() {
        server.onFixture("/PesquisarPostos", "dgeg-diesel.json");
        List<StationPrice> prices = new PortugalDgegProvider(server.baseUrl(), http)
                .stationPrices(LocalDate.of(2026, 10, 30), FuelType.DIESEL);

        assertEquals(2, prices.size());
        assertEquals(new BigDecimal("1.659"), prices.getFirst().price());
        assertEquals(38.705, prices.get(1).latitude(), 1e-9);
        assertTrue(server.requests().getFirst().query().contains("idsTiposComb=2101"));
    }

    @Test
    void aMissingServiceIsAnExternalApiException() {
        server.close();
        ExternalApiException e = assertThrows(ExternalApiException.class, () -> new PortugalDgegProvider(
                "http://127.0.0.1:1", http).stationPrices(LocalDate.now(), FuelType.DIESEL));
        assertEquals(0, e.status());
    }

    /** A refusal as Google writes it, with its {@code google.rpc.ErrorInfo}. */
    private static FakeHttpServer.Response refusal(int status, String reason, String service) {
        return new FakeHttpServer.Response(status, """
                {"error": {"code": %d, "message": "Request refused", "status": "PERMISSION_DENIED",
                  "details": [{"@type": "type.googleapis.com/google.rpc.ErrorInfo", "reason": "%s",
                    "domain": "googleapis.com", "metadata": {"service": "%s", "consumer": "projects/123"}}]}}
                """.formatted(status, reason, service));
    }

    private MobileClient mobile(String secret) {
        return new MobileClient(() -> new MobileClient.Connection(server.baseUrl() + "/exec", secret), http);
    }

    private RoutesClient routes() {
        return new RoutesClient(() -> google, http, CLOCK);
    }

    private static Place place(String googleId) {
        return new Place(1L, googleId, googleId, null, 40.0, -3.0, Country.ES);
    }
}
