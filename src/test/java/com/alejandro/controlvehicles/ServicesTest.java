package com.alejandro.controlvehicles;

import com.alejandro.controlvehicles.app.AppContext;
import com.alejandro.controlvehicles.domain.Country;
import com.alejandro.controlvehicles.domain.Divergence;
import com.alejandro.controlvehicles.domain.DivergenceKind;
import com.alejandro.controlvehicles.domain.Driver;
import com.alejandro.controlvehicles.domain.FuelPrice;
import com.alejandro.controlvehicles.domain.FuelType;
import com.alejandro.controlvehicles.domain.Place;
import com.alejandro.controlvehicles.domain.PriceQuote;
import com.alejandro.controlvehicles.domain.Refuel;
import com.alejandro.controlvehicles.domain.TollStatus;
import com.alejandro.controlvehicles.domain.Trip;
import com.alejandro.controlvehicles.domain.Vehicle;
import com.alejandro.controlvehicles.integration.google.PlaceSuggestion;
import com.alejandro.controlvehicles.service.AppSettings;
import com.alejandro.controlvehicles.service.MonthlyReport;
import com.alejandro.controlvehicles.service.SaveResult;
import com.alejandro.controlvehicles.service.TripRow;
import com.alejandro.controlvehicles.service.TripService;
import com.alejandro.controlvehicles.service.ValidationException;
import com.alejandro.controlvehicles.support.FakeHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static com.alejandro.controlvehicles.support.Fixtures.withEnd;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The services wired as the application wires them, against a local server that plays Google and MITECO. */
class ServicesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"), ZoneId.of("Europe/Madrid"));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);

    @TempDir
    Path home;
    private FakeHttpServer server;
    private AppContext app;
    private Driver driver;
    private Vehicle vehicle;
    private Place madrid;
    private Place toledo;

    @BeforeEach
    void start() {
        server = new FakeHttpServer()
                .onFixture("/v1/places:autocomplete", "places-autocomplete.json")
                .onFixture("/v1/places/ChIJgTwKgJcpQg0RaSKMYcHeNsQ", "places-details-madrid.json")
                .onFixture("/v1/places/ChIJ8f21C60Lag0R_q11auhbf8Y", "places-details-toledo.json")
                .onFixture("/directions/v2:computeRoutes", "routes-with-tolls.json")
                .onFixture("/miteco/EstacionesTerrestres", "miteco-stations.json")
                .onFixture("/dgeg/PesquisarPostos", "dgeg-diesel.json");
        String base = server.baseUrl();
        app = new AppContext(home, CLOCK, new AppContext.Endpoints(base, base, base + "/miteco", base + "/dgeg"));
        app.settings.saveGoogleApiKey("test-key");
        driver = app.fleet.save(new Driver(null, "Ana Pérez", null, null, null, true, null));
        vehicle = app.fleet.save(new Vehicle(null, "1234 ABC", "Renault", "Kangoo", FuelType.DIESEL,
                new BigDecimal("6"), new BigDecimal("60"), 100_000, true, null));
        madrid = app.places.resolve(new PlaceSuggestion("ChIJgTwKgJcpQg0RaSKMYcHeNsQ", "Madrid", "Spain"), "s");
        toledo = app.places.resolve(new PlaceSuggestion("ChIJ8f21C60Lag0R_q11auhbf8Y", "Toledo", "Spain"), "s");
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void savingATripAddsGooglesRouteAndTheGalpPriceAroundTheOrigin() {
        SaveResult<Trip> result = app.trips.save(trip(TODAY, "08:00", "09:10", madrid, toledo, 100_000), null);

        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
        Trip saved = result.saved();
        assertEquals(72_450, saved.route().distanceMeters());
        assertEquals(TollStatus.PRICED, saved.route().tollStatus());
        // Las dos Galp de Madrid (1,459 y 1,479); Repsol queda fuera por la marca y Toledo por la distancia.
        assertEquals(new BigDecimal("1.469"), saved.price().pricePerLitre());
        assertTrue(saved.price().source().contains("2 GALP stations within 25 km"), saved.price().source());
        assertTrue(server.requests("/directions").getFirst().body().contains("ChIJgTwKgJcpQg0RaSKMYcHeNsQ"));
    }

    @Test
    void theStationQueryAlsoStoresTheCountryAveragesOfTheDay() {
        app.trips.save(trip(TODAY, "08:00", "09:10", madrid, toledo, 100_000), null);

        List<FuelPrice> stored = app.fuelPrices.all();
        assertEquals(2, stored.size(), "all brands and GALP, diesel");
        FuelPrice galp = stored.stream().filter(p -> "GALP".equals(p.brand())).findFirst().orElseThrow();
        assertEquals(2, galp.stations(), "Galp stations country-wide with a diesel price and coordinates");
    }

    @Test
    void withoutGoogleTheTripIsSavedAnywayAndSaysWhatIsMissing() {
        server.on("/directions/v2:computeRoutes", r -> new FakeHttpServer.Response(503, "{}"));
        SaveResult<Trip> result = app.trips.save(trip(TODAY, "08:00", "09:10", madrid, toledo, 100_000), null);

        assertNull(result.saved().route());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().getFirst().startsWith("Google route not calculated"));

        server.onFixture("/directions/v2:computeRoutes", "routes-with-tolls.json");
        assertNotNull(app.trips.recalculate(result.saved().id()).saved().route());
    }

    @Test
    void pendingTripsAreCompletedInOneGo() {
        server.on("/directions/v2:computeRoutes", r -> new FakeHttpServer.Response(503, "{}"));
        app.trips.save(trip(TODAY.minusDays(2), "08:00", "09:10", madrid, toledo, 100_000), null);
        app.trips.save(trip(TODAY.minusDays(1), "08:00", "09:10", toledo, madrid, 100_080),
                PriceQuote.manual(new BigDecimal("1.399")));
        assertEquals(2, app.trips.pending().size());

        server.onFixture("/directions/v2:computeRoutes", "routes-with-tolls.json");
        TripService.PendingResult result = app.trips.completePending();

        assertEquals(2, result.trips());
        assertEquals(2, result.routesAdded());
        assertFalse(result.googleStopped());
        assertTrue(app.trips.pending().isEmpty());
        assertEquals(new BigDecimal("1.399"), app.trips.rows().getFirst().trip().price().pricePerLitre(),
                "a manual price is kept");
    }

    @Test
    void withoutAKeyGoogleIsAskedOnceAndPricesAreStillCompleted() {
        server.on("/miteco/EstacionesTerrestres", r -> new FakeHttpServer.Response(503, "{}"));
        for (int day = 1; day <= 3; day++) {
            server.on("/directions/v2:computeRoutes", r -> new FakeHttpServer.Response(503, "{}"));
            app.trips.save(trip(TODAY.minusDays(day), "08:00", "09:10", madrid, toledo, 100_000 + day * 100), null);
        }
        assertTrue(app.trips.pending().stream().allMatch(t -> t.price() == null && t.route() == null));
        app.settings.saveGoogleApiKey("");
        server.onFixture("/miteco/EstacionesTerrestres", "miteco-stations.json");
        int googleCalls = server.requests("/directions").size();

        TripService.PendingResult result = app.trips.completePending();

        assertTrue(result.googleStopped());
        assertEquals(0, result.routesAdded());
        assertEquals(3, result.pricesAdded());
        assertEquals(googleCalls, server.requests("/directions").size(), "no key: nothing sent to Google");
        assertTrue(result.problems().stream().anyMatch(p -> p.contains("No Google API key")), result.problems()
                .toString());
    }

    @Test
    void aManualPriceWinsAndSurvivesARecalculation() {
        Trip saved = app.trips.save(trip(TODAY, "08:00", "09:10", madrid, toledo, 100_000),
                PriceQuote.manual(new BigDecimal("1.399"))).saved();
        assertEquals("Manual", saved.price().source());
        assertEquals(new BigDecimal("1.399"), app.trips.recalculate(saved.id()).saved().price().pricePerLitre());
    }

    @Test
    void editingATripWithoutChangingItsPlacesDoesNotCallGoogleAgain() {
        Trip saved = app.trips.save(trip(TODAY, "08:00", "09:10", madrid, toledo, 100_000), null).saved();
        int calls = server.requests("/directions").size();
        Trip edited = new Trip(saved.id(), saved.vehicleId(), saved.driverId(), saved.date(), saved.startTime(),
                LocalTime.of(9, 30), saved.originPlaceId(), saved.destinationPlaceId(), saved.startOdometerKm(),
                saved.endOdometerKm(), new BigDecimal("7.45"), "edited", saved.route(), saved.price());
        app.trips.save(edited, null);
        assertEquals(calls, server.requests("/directions").size());
    }

    @Test
    void aPastDayWithoutStationPricesFallsBackToTheClosestStoredPrice() {
        server.on("/miteco/EstacionesTerrestresHist", r -> new FakeHttpServer.Response(500, "{}"));
        app.fuelPrices.saveManual(FuelPrice.manual(LocalDate.of(2026, 9, 28), Country.ES, FuelType.DIESEL, "GALP",
                new BigDecimal("1.480")));
        Trip saved = app.trips.save(trip(LocalDate.of(2026, 10, 1), "08:00", "09:10", madrid, toledo, 100_000),
                null).saved();
        assertEquals(new BigDecimal("1.480"), saved.price().pricePerLitre());
        assertTrue(saved.price().source().contains("of 2026-09-28"), saved.price().source());
    }

    @Test
    void portugalHasNoHistorySoAPastDayUsesWhatWasStored() {
        Place lisbon = app.places.findById(app.places.resolve(
                new PlaceSuggestion("ChIJgTwKgJcpQg0RaSKMYcHeNsQ", "x", null), "s").id()).orElseThrow();
        assertEquals(Country.ES, lisbon.country());
        List<FuelPrice> today = app.fuelPrices.fetchToday(Country.PT);
        assertEquals(4, today.size(), "both fuels, all brands and GALP");
        Optional<PriceQuote> quote = app.fuelPrices.quote(new Place(null, null, "Lisboa", null, 38.72, -9.14,
                Country.PT), TODAY.minusDays(5), FuelType.DIESEL);
        assertTrue(quote.orElseThrow().source().startsWith("DGEG PT GALP country avg"), quote.get().source());
        assertEquals(1, server.requests("/dgeg").stream().filter(r -> r.query().contains("2101")).count(),
                "a past day does not ask DGEG");
    }

    @Test
    void theTripListDerivesTheOdometerDistanceAndFlagsAnUnregisteredReturn() {
        app.trips.save(trip(TODAY.minusDays(2), "08:00", "09:10", madrid, toledo, 100_000), null);
        app.trips.save(trip(TODAY.minusDays(1), "08:00", "09:10", madrid, toledo, 100_150), null);

        List<TripRow> rows = app.trips.rows();
        assertEquals(2, rows.size());
        TripRow latest = rows.getFirst();
        TripRow first = rows.get(1);
        assertTrue(latest.pending());
        assertEquals(150, first.odometerKm());
        assertEquals(0, new BigDecimal("77.55").compareTo(first.gapKm()));
        assertEquals(DivergenceKind.DISTANCE_MISMATCH, first.divergences().getFirst().kind());
    }

    @Test
    void anEndOdometerSeparatesTheTripFromTheUseNobodyRegistered() {
        Trip first = app.trips.save(withEnd(trip(LocalDate.of(2026, 10, 1), "08:00", "09:10", madrid, toledo,
                100_000), 100_074), PriceQuote.manual(new BigDecimal("1.500"))).saved();
        app.trips.save(trip(LocalDate.of(2026, 10, 2), "08:00", "09:10", toledo, madrid, 100_150),
                PriceQuote.manual(new BigDecimal("1.500")));

        TripRow row = app.trips.rows().stream().filter(r -> r.trip().id().equals(first.id())).findFirst()
                .orElseThrow();
        assertEquals(74, row.odometerKm());
        assertEquals(76, row.unrecordedKm());
        assertEquals(List.of(DivergenceKind.UNRECORDED_USE),
                row.divergences().stream().map(Divergence::kind).filter(k -> k != DivergenceKind.TOLL_MISMATCH)
                        .toList(), "the trip itself agrees with Google: only the gap is a divergence");

        MonthlyReport.Line line = app.reports.monthly(YearMonth.of(2026, 10)).vehicles().getFirst();
        assertEquals(74, line.odometerKm());
        assertEquals(76, line.unrecordedKm());
        assertEquals(1, line.pendingTrips());
        assertEquals(76, app.reports.monthly(YearMonth.of(2026, 10)).drivers().getFirst().unrecordedKm());
    }

    @Test
    void anEndOdometerBelowTheStartIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> withEnd(trip(TODAY, "08:00", "09:10", madrid, toledo,
                100_100), 100_050));
    }

    @Test
    void theSuggestedOdometerIsTheHighestReadingBeforeTheTrip() {
        app.trips.save(trip(TODAY.minusDays(2), "08:00", "09:10", madrid, toledo, 100_000), null);
        app.refuels.save(refuel(LocalDateTime.of(2026, 10, 28, 12, 0), 100_090, "40", "58.80", true));
        assertEquals(100_090, app.trips.suggestedStartOdometer(vehicle.id(), TODAY.atTime(8, 0)));
        assertEquals(100_000, app.trips.suggestedStartOdometer(vehicle.id(), TODAY.minusDays(2).atTime(10, 0)));
        assertEquals(100_000, app.trips.suggestedStartOdometer(vehicle.id(), TODAY.minusDays(10).atTime(8, 0)),
                "nothing before: the initial odometer");
        app.trips.save(withEnd(trip(TODAY, "08:00", "09:10", madrid, toledo, 100_100), 100_172), null);
        assertEquals(100_172, app.trips.suggestedStartOdometer(vehicle.id(), TODAY.atTime(12, 0)),
                "a noted end is the latest reading");
    }

    @Test
    void anOdometerBelowTheVehiclesInitialOneIsRefused() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> app.trips.save(trip(TODAY, "08:00", "09:10", madrid, toledo, 99_999), null));
        assertTrue(e.getMessage().contains("100000"));
    }

    @Test
    void theMonthlyReportComparesRealSpendWithExpectedCosts() {
        app.trips.save(trip(LocalDate.of(2026, 10, 1), "08:00", "09:10", madrid, toledo, 100_000),
                PriceQuote.manual(new BigDecimal("1.500")));
        app.trips.save(trip(LocalDate.of(2026, 10, 2), "08:00", "09:10", madrid, toledo, 100_150),
                PriceQuote.manual(new BigDecimal("1.500")));
        app.trips.save(trip(LocalDate.of(2026, 11, 2), "08:00", "09:10", madrid, toledo, 100_300),
                PriceQuote.manual(new BigDecimal("1.500")));
        app.refuels.save(refuel(LocalDateTime.of(2026, 10, 1, 7, 0), 100_000, "40", "60", true));
        app.refuels.save(refuel(LocalDateTime.of(2026, 10, 30, 7, 0), 100_300, "24", "36", true));

        MonthlyReport report = app.reports.monthly(YearMonth.of(2026, 10));
        MonthlyReport.Line line = report.vehicles().getFirst();
        assertEquals(2, line.trips());
        assertEquals(0, line.pendingTrips(), "the November trip closes the second one");
        assertEquals(300, line.odometerKm());
        assertEquals(0, new BigDecimal("144.9").compareTo(line.googleKm()));
        // 72,45 km × 6 l/100 × 1,5 €/l = 6,52 € por trayecto.
        assertEquals(new BigDecimal("13.04"), line.expectedCost());
        assertEquals(new BigDecimal("27.00"), line.odometerCost());
        assertEquals(new BigDecimal("96"), line.realSpend());
        assertEquals(new BigDecimal("8.00"), line.measuredL100());
        assertEquals(new BigDecimal("14.90"), line.tollsEstimated());
        assertEquals(5, line.divergences(),
                "two distances (150 vs 72 km), two tolls (none declared, 7,45 € by Google), consumption (8 vs 6)");
        assertEquals("Ana Pérez", report.drivers().getFirst().name());
        assertEquals(line.realSpend(), report.total().realSpend());
    }

    @Test
    void theReportIsExportedAsCsvThatExcelOpens() throws Exception {
        app.trips.save(trip(LocalDate.of(2026, 10, 1), "08:00", "09:10", madrid, toledo, 100_000),
                PriceQuote.manual(new BigDecimal("1.500")));
        Path file = home.resolve("report.csv");
        app.csv.writeReport(app.reports.monthly(YearMonth.of(2026, 10)), file);

        String csv = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("﻿Group;Name;Trips"));
        assertTrue(csv.contains("Vehicle;1234 ABC · Renault Kangoo;1;1;0;0;0;72,45;0;6;"), csv);
        assertTrue(csv.contains("Total 2026-10"));
    }

    @Test
    void theRulesTravelWithTheDatabaseButTheGoogleKeyDoesNot() throws Exception {
        app.settings.save(new AppSettings(AppSettings.DEFAULTS.thresholds(), 10, "GALP", Country.PT));
        Path exported = home.resolve("handover/fleet.db");
        Files.createDirectories(exported.getParent());
        app.transfer.exportTo(exported);

        assertFalse(new String(Files.readAllBytes(exported), StandardCharsets.ISO_8859_1).contains("test-key"));
        Path otherHome = home.resolve("other");
        AppContext other = new AppContext(otherHome, CLOCK);
        other.transfer.importFrom(exported);
        assertEquals(10, other.settings.load().priceRadiusKm());
        assertEquals(Country.PT, other.settings.load().defaultCountry());
        assertEquals("", other.settings.googleApiKey());
        assertEquals("1234 ABC", other.fleet.vehicles().getFirst().plate());
    }

    @Test
    void importingReplacesTheDataAndKeepsABackupOfThePreviousOne() throws Exception {
        app.trips.save(trip(TODAY, "08:00", "09:10", madrid, toledo, 100_000), null);
        Path exported = home.resolve("copy.db");
        app.transfer.exportTo(exported);
        app.fleet.save(new Driver(null, "Added after the export", null, null, null, true, null));

        Path backup = app.transfer.importFrom(exported);

        assertTrue(Files.exists(backup));
        assertEquals(1, app.fleet.drivers().size());
        assertEquals(1, app.trips.rows().size());
        AppContext fromBackup = new AppContext(home.resolve("restored"), CLOCK);
        fromBackup.transfer.importFrom(backup);
        assertEquals(2, fromBackup.fleet.drivers().size());
    }

    @Test
    void anythingElseIsNotImported() throws Exception {
        Path text = home.resolve("notes.db");
        Files.writeString(text, "not a database");
        assertThrows(ValidationException.class, () -> app.transfer.importFrom(text));
        Path foreign = home.resolve("foreign.db");
        try (var c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + foreign)) {
            c.createStatement().execute("CREATE TABLE x (id INTEGER)");
        }
        ValidationException e = assertThrows(ValidationException.class, () -> app.transfer.importFrom(foreign));
        assertTrue(e.getMessage().contains("not a control-vehicles database"));
        assertEquals(1, app.fleet.drivers().size(), "the current data is untouched");
    }

    @Test
    void theAnalysisSeesRefuelDivergences() {
        app.trips.save(trip(TODAY.minusDays(2), "08:00", "09:10", madrid, toledo, 100_000), null);
        app.trips.save(trip(TODAY.minusDays(1), "08:00", "09:10", madrid, toledo, 100_080), null);
        app.refuels.save(refuel(LocalDateTime.of(2026, 10, 28, 12, 0), 100_200, "65", "95", true));

        List<DivergenceKind> kinds = app.analysis.analyse().divergences().stream().map(Divergence::kind).toList();
        assertTrue(kinds.contains(DivergenceKind.REFUEL_ODOMETER_OUT_OF_SEQUENCE), kinds.toString());
        assertTrue(kinds.contains(DivergenceKind.REFUEL_OVER_CAPACITY), kinds.toString());
    }

    private Trip trip(LocalDate date, String start, String end, Place origin, Place destination, int odometer) {
        return new Trip(null, vehicle.id(), driver.id(), date, LocalTime.parse(start), LocalTime.parse(end),
                origin.id(), destination.id(), odometer, null, BigDecimal.ZERO, null, null, null);
    }

    private Refuel refuel(LocalDateTime at, int odometer, String litres, String amount, boolean full) {
        return new Refuel(null, vehicle.id(), driver.id(), at, odometer, false, new BigDecimal(litres),
                new BigDecimal(amount), full, null, "Galp", Country.ES, null);
    }
}
