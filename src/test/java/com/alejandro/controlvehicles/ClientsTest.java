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
import com.alejandro.controlvehicles.support.FakeHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private RoutesClient routes() {
        return new RoutesClient(() -> google, http, CLOCK);
    }

    private static Place place(String googleId) {
        return new Place(1L, googleId, googleId, null, 40.0, -3.0, Country.ES);
    }
}
