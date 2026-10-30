package com.alejandro.controlvehicles;

import com.alejandro.controlvehicles.domain.Country;
import com.alejandro.controlvehicles.domain.Driver;
import com.alejandro.controlvehicles.domain.FuelPrice;
import com.alejandro.controlvehicles.domain.FuelType;
import com.alejandro.controlvehicles.domain.Place;
import com.alejandro.controlvehicles.domain.PriceQuote;
import com.alejandro.controlvehicles.domain.PriceSource;
import com.alejandro.controlvehicles.domain.Refuel;
import com.alejandro.controlvehicles.domain.RouteEstimate;
import com.alejandro.controlvehicles.domain.TollStatus;
import com.alejandro.controlvehicles.domain.Trip;
import com.alejandro.controlvehicles.domain.Vehicle;
import com.alejandro.controlvehicles.persistence.DataAccessException;
import com.alejandro.controlvehicles.persistence.Database;
import com.alejandro.controlvehicles.persistence.DriverRepository;
import com.alejandro.controlvehicles.persistence.FuelPriceRepository;
import com.alejandro.controlvehicles.persistence.MigrationRunner;
import com.alejandro.controlvehicles.persistence.PlaceRepository;
import com.alejandro.controlvehicles.persistence.RefuelRepository;
import com.alejandro.controlvehicles.persistence.TripRepository;
import com.alejandro.controlvehicles.persistence.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceTest {

    @TempDir
    Path dir;
    private Database database;

    @BeforeEach
    void open() {
        database = new Database(dir.resolve("data/test.db")).initialize();
    }

    @Test
    void migrationsRunOnceAndMarkTheFileAsOurs() {
        database.initialize();
        int versions = database.read(c -> {
            try (ResultSet rs = c.createStatement().executeQuery("SELECT COUNT(*) FROM schema_version")) {
                return rs.getInt(1);
            }
        });
        int applicationId = database.read(c -> {
            try (ResultSet rs = c.createStatement().executeQuery("PRAGMA application_id")) {
                return rs.getInt(1);
            }
        });
        assertEquals(MigrationRunner.latestVersion(), versions);
        assertEquals(Database.APPLICATION_ID, applicationId);
    }

    @Test
    void aTripKeepsEverythingItWasSavedWith() {
        Driver driver = new DriverRepository(database).save(new Driver(null, "Ana Pérez", "B-123", null, null, true,
                null));
        Vehicle vehicle = new VehicleRepository(database).save(new Vehicle(null, "1234 abc", "Renault", "Kangoo",
                FuelType.DIESEL, new BigDecimal("5.8"), new BigDecimal("60"), 120_000, true, null));
        PlaceRepository places = new PlaceRepository(database);
        Place origin = places.save(new Place(null, "p1", "Madrid", "Madrid, Spain", 40.4, -3.7, Country.ES));
        Place destination = places.save(new Place(null, "p2", "Toledo", "Toledo, Spain", 39.8, -4.0, Country.ES));
        TripRepository trips = new TripRepository(database);
        Trip saved = trips.save(new Trip(null, vehicle.id(), driver.id(), LocalDate.of(2026, 10, 1),
                LocalTime.of(8, 0), LocalTime.of(9, 15), origin.id(), destination.id(), 120_500,
                new BigDecimal("7.45"), "client visit",
                new RouteEstimate(72_450, 3960, TollStatus.PRICED, new BigDecimal("7.45"),
                        Instant.parse("2026-10-01T10:00:00Z")),
                new PriceQuote(new BigDecimal("1.459"), "MITECO")));

        Trip read = trips.findById(saved.id()).orElseThrow();
        assertEquals(saved, read);
        assertEquals("1234 ABC", vehicle.plate());

        Trip cleared = trips.save(read.withRoute(null).withPrice(null));
        assertNull(trips.findById(cleared.id()).orElseThrow().route());
    }

    @Test
    void aGooglePlaceIsStoredOnceAndFoundByText() {
        PlaceRepository places = new PlaceRepository(database);
        Place first = places.save(new Place(null, "abc", "Galp Alcalá", "Calle de Alcalá 1", 40.0, -3.0, Country.ES));
        Place again = places.save(new Place(null, "abc", "Galp Alcalá", "Calle de Alcalá 1, Madrid", 40.0, -3.0,
                Country.ES));
        assertEquals(first.id(), again.id());
        places.markUsed(first.id());
        assertEquals(1, places.search("alcal", 5).size());
        assertEquals(1, places.findAll().size());
    }

    @Test
    void aDriverWithTripsCannotBeDeletedAndTheMessageSaysWhy() {
        DriverRepository drivers = new DriverRepository(database);
        Driver driver = drivers.save(new Driver(null, "Ana", null, null, null, true, null));
        Vehicle vehicle = new VehicleRepository(database).save(new Vehicle(null, "1", null, null, FuelType.GASOLINE,
                new BigDecimal("7"), null, 0, true, null));
        new RefuelRepository(database).save(new Refuel(null, vehicle.id(), driver.id(),
                LocalDateTime.of(2026, 10, 1, 9, 0), 10, new BigDecimal("30"), new BigDecimal("45"), true, null,
                "Galp", Country.PT, null));

        DataAccessException e = assertThrows(DataAccessException.class, () -> drivers.delete(driver.id()));
        assertTrue(e.getMessage().contains("deactivate"));
    }

    @Test
    void aRepeatedPlateIsRefusedWithAReadableMessage() {
        VehicleRepository vehicles = new VehicleRepository(database);
        vehicles.save(new Vehicle(null, "1234ABC", null, null, FuelType.DIESEL, new BigDecimal("6"), null, 0, true,
                null));
        DataAccessException e = assertThrows(DataAccessException.class, () -> vehicles.save(new Vehicle(null,
                "1234abc", null, null, FuelType.DIESEL, new BigDecimal("6"), null, 0, true, null)));
        assertEquals("There is already one with the same plate.", e.getMessage());
    }

    @Test
    void theClosestStoredPricePrefersTheBrandThenTheDayThenAManualOne() {
        FuelPriceRepository prices = new FuelPriceRepository(database);
        LocalDate day = LocalDate.of(2026, 10, 15);
        prices.upsert(price(day.minusDays(3), "GALP", "1.500", PriceSource.MITECO));
        prices.upsert(price(day, null, "1.400", PriceSource.MITECO));
        prices.upsert(price(day.minusDays(1), "GALP", "1.520", PriceSource.MITECO));
        prices.upsert(price(day.minusDays(1), "GALP", "1.530", PriceSource.MANUAL));

        assertEquals(new BigDecimal("1.530"),
                prices.closest(day, Country.ES, FuelType.DIESEL, "GALP").orElseThrow().averagePrice());
        assertEquals(new BigDecimal("1.400"),
                prices.closest(day, Country.ES, FuelType.DIESEL, null).orElseThrow().averagePrice());
        assertTrue(prices.closest(day, Country.PT, FuelType.DIESEL, "GALP").isEmpty());

        prices.upsert(price(day.minusDays(1), "GALP", "1.540", PriceSource.MITECO));
        assertEquals(4, prices.findAll().size(), "same day, brand and source replaces");
    }

    private static FuelPrice price(LocalDate date, String brand, String value, PriceSource source) {
        BigDecimal price = new BigDecimal(value);
        return new FuelPrice(null, date, Country.ES, FuelType.DIESEL, brand, price, price, price, 3, source, null);
    }
}
