package com.alejandro.controlvehicles.app;

import com.alejandro.controlvehicles.integration.HttpJson;
import com.alejandro.controlvehicles.integration.fuel.PortugalDgegProvider;
import com.alejandro.controlvehicles.integration.fuel.SpainMitecoProvider;
import com.alejandro.controlvehicles.integration.google.GoogleSettings;
import com.alejandro.controlvehicles.integration.google.PlacesClient;
import com.alejandro.controlvehicles.integration.google.RoutesClient;
import com.alejandro.controlvehicles.persistence.Database;
import com.alejandro.controlvehicles.persistence.DriverRepository;
import com.alejandro.controlvehicles.persistence.FuelPriceRepository;
import com.alejandro.controlvehicles.persistence.PlaceRepository;
import com.alejandro.controlvehicles.persistence.RefuelRepository;
import com.alejandro.controlvehicles.persistence.SettingRepository;
import com.alejandro.controlvehicles.persistence.TripRepository;
import com.alejandro.controlvehicles.persistence.VehicleRepository;
import com.alejandro.controlvehicles.service.AnalysisService;
import com.alejandro.controlvehicles.service.CsvExporter;
import com.alejandro.controlvehicles.service.DatabaseTransferService;
import com.alejandro.controlvehicles.service.FleetService;
import com.alejandro.controlvehicles.service.FuelPriceService;
import com.alejandro.controlvehicles.service.PlaceService;
import com.alejandro.controlvehicles.service.RefuelService;
import com.alejandro.controlvehicles.service.ReportService;
import com.alejandro.controlvehicles.service.SettingsService;
import com.alejandro.controlvehicles.service.TripService;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * Every piece of the application, wired by hand. The data lives in {@code ~/.control-vehicles} unless the
 * system property {@code controlvehicles.home} says otherwise.
 */
public final class AppContext {

    public final Database database;
    public final SettingsService settings;
    public final FleetService fleet;
    public final PlaceService places;
    public final TripService trips;
    public final RefuelService refuels;
    public final FuelPriceService fuelPrices;
    public final AnalysisService analysis;
    public final ReportService reports;
    public final DatabaseTransferService transfer;
    public final CsvExporter csv = new CsvExporter();

    /** Where the external services are; only the tests point them elsewhere. */
    public record Endpoints(String googlePlaces, String googleRoutes, String miteco, String dgeg) {

        public static final Endpoints PRODUCTION = new Endpoints("https://places.googleapis.com",
                "https://routes.googleapis.com", SpainMitecoProvider.DEFAULT_BASE_URL,
                PortugalDgegProvider.DEFAULT_BASE_URL);
    }

    public AppContext(Path home, Clock clock) {
        this(home, clock, Endpoints.PRODUCTION);
    }

    public AppContext(Path home, Clock clock, Endpoints endpoints) {
        database = new Database(home.resolve("control-vehicles.db")).initialize();
        settings = new SettingsService(new SettingRepository(database), home.resolve("settings.properties"));

        DriverRepository driverRepository = new DriverRepository(database);
        VehicleRepository vehicleRepository = new VehicleRepository(database);
        PlaceRepository placeRepository = new PlaceRepository(database);
        TripRepository tripRepository = new TripRepository(database);
        RefuelRepository refuelRepository = new RefuelRepository(database);

        HttpJson google = new HttpJson(Duration.ofSeconds(15));
        HttpJson fuel = new HttpJson(Duration.ofSeconds(90));
        Supplier<GoogleSettings> googleSettings = () -> new GoogleSettings(settings.googleApiKey(),
                endpoints.googlePlaces(), endpoints.googleRoutes(), "en");
        fleet = new FleetService(driverRepository, vehicleRepository);
        places = new PlaceService(placeRepository, new PlacesClient(googleSettings, google));
        analysis = new AnalysisService(vehicleRepository, tripRepository, refuelRepository, settings::load);
        fuelPrices = new FuelPriceService(List.of(
                new SpainMitecoProvider(endpoints.miteco(), fuel, clock),
                new PortugalDgegProvider(endpoints.dgeg(), fuel)),
                new FuelPriceRepository(database), settings::load, clock);
        trips = new TripService(tripRepository, vehicleRepository, driverRepository, placeRepository,
                refuelRepository, new RoutesClient(googleSettings, google, clock), fuelPrices, analysis);
        refuels = new RefuelService(refuelRepository, vehicleRepository, driverRepository, analysis);
        reports = new ReportService(vehicleRepository, driverRepository, tripRepository, refuelRepository, analysis);
        transfer = new DatabaseTransferService(database, clock);
    }

    public static Path defaultHome() {
        String configured = System.getProperty("controlvehicles.home");
        return configured != null && !configured.isBlank() ? Path.of(configured)
                : Path.of(System.getProperty("user.home"), ".control-vehicles");
    }
}
