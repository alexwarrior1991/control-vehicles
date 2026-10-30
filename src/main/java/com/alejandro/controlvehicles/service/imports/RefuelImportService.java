package com.alejandro.controlvehicles.service.imports;

import com.alejandro.controlvehicles.domain.OdometerEstimator;
import com.alejandro.controlvehicles.domain.Refuel;
import com.alejandro.controlvehicles.domain.Trip;
import com.alejandro.controlvehicles.domain.Vehicle;
import com.alejandro.controlvehicles.persistence.RefuelRepository;
import com.alejandro.controlvehicles.persistence.SettingRepository;
import com.alejandro.controlvehicles.persistence.TripRepository;
import com.alejandro.controlvehicles.persistence.VehicleRepository;
import com.alejandro.controlvehicles.service.AppSettings;
import com.alejandro.controlvehicles.service.Inputs;
import com.alejandro.controlvehicles.service.ValidationException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Refuels from a fuel card statement (Galp Frota or any other), with the columns chosen by the user. Every
 * refuel is Galp, full tank (to untick by hand when it was not), of the default country; one whose line has no
 * odometer gets the estimate of {@link OdometerEstimator}, marked as such. Lines of an unknown plate, of something
 * that is not fuel, or already imported (same vehicle, minute, litres and amount) are left out and told.
 */
public final class RefuelImportService {

    static final String MAPPING_KEY = "import.refuels.mapping";
    /** Words of a product that is not fuel. Never "oleo" or "oil": gasóleo and gas oil are diesel. */
    private static final List<String> NOT_FUEL = List.of("adblue", "ad blue", "lavag", "lavad", "wash", "aceite",
            "lubri", "loja", "tienda", "shop", "portag", "peaje", "toll", "parking", "estaciona");

    private final VehicleRepository vehicles;
    private final TripRepository trips;
    private final RefuelRepository refuels;
    private final SettingRepository settings;
    private final Supplier<AppSettings> appSettings;

    public RefuelImportService(VehicleRepository vehicles, TripRepository trips, RefuelRepository refuels,
                               SettingRepository settings, Supplier<AppSettings> appSettings) {
        this.vehicles = vehicles;
        this.trips = trips;
        this.refuels = refuels;
        this.settings = settings;
        this.appSettings = appSettings;
    }

    public TabularFile read(Path file) {
        String name = file.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".xls")) {
            throw new ValidationException("Old .xls files cannot be read: save it as .xlsx or CSV and import that");
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new ValidationException("Cannot read " + name + ": " + e.getMessage());
        }
        TabularFile table = lower.endsWith(".xlsx") ? XlsxReader.read(name, bytes) : CsvReader.read(name, bytes);
        if (table.header().isEmpty()) {
            throw new ValidationException(name + " is empty");
        }
        return table;
    }

    /** The mapping used last time, if every one of its columns is in this file; otherwise a guess. */
    public RefuelImportMapping proposedMapping(TabularFile table) {
        RefuelImportMapping saved = RefuelImportMapping.fromJson(settings.findAll().get(MAPPING_KEY)).orElse(null);
        if (saved != null && !saved.columns().isEmpty()
                && saved.restrictedTo(table.header()).columns().size() == saved.columns().size()) {
            return saved;
        }
        return RefuelImportMapping.guess(table.header());
    }

    public List<ImportRow> preview(TabularFile table, RefuelImportMapping mapping) {
        if (!mapping.missingRequired().isEmpty()) {
            throw new ValidationException("Choose the column of: " + mapping.missingRequired().stream()
                    .map(ImportField::label).collect(Collectors.joining(", ")));
        }
        Map<String, Vehicle> byPlate = vehicles.findAll().stream()
                .collect(Collectors.toMap(v -> plateKey(v.plate()), Function.identity(), (a, b) -> a));
        List<Trip> allTrips = trips.findAll();
        List<Refuel> known = new ArrayList<>(refuels.findAll());
        Set<String> seen = known.stream().map(RefuelImportService::duplicateKey)
                .collect(Collectors.toCollection(HashSet::new));
        String brand = appSettings.get().preferredBrand() == null ? "Galp" : capitalised(appSettings.get()
                .preferredBrand());

        List<ImportRow> parsed = new ArrayList<>();
        List<Parsed> pending = new ArrayList<>();
        for (int i = 0; i < table.rows().size(); i++) {
            List<String> row = table.rows().get(i);
            int line = i + 2;
            String plate = cell(table, row, mapping, ImportField.PLATE);
            String product = cell(table, row, mapping, ImportField.PRODUCT);
            if (product != null && isNotFuel(product)) {
                parsed.add(new ImportRow(line, ImportRow.Status.NOT_FUEL, plate, null, product));
                continue;
            }
            Vehicle vehicle = plate == null ? null : byPlate.get(plateKey(plate));
            if (vehicle == null) {
                parsed.add(new ImportRow(line, ImportRow.Status.UNKNOWN_PLATE, plate, null,
                        plate == null ? "No plate" : "No vehicle with plate " + plate));
                continue;
            }
            try {
                pending.add(new Parsed(line, plate, vehicle, parse(table, row, mapping), product));
            } catch (RuntimeException e) {
                parsed.add(new ImportRow(line, ImportRow.Status.INVALID, plate, null, e.getMessage()));
            }
        }

        // En orden de fecha: un repostaje con km leído del extracto ayuda a estimar los que vienen detrás.
        pending.sort(Comparator.comparing((Parsed p) -> p.values().at()).thenComparingInt(Parsed::line));
        for (Parsed p : pending) {
            Values v = p.values();
            boolean estimated = v.odometer() == null;
            int odometer = estimated
                    ? OdometerEstimator.estimate(p.vehicle(), allTrips.stream()
                            .filter(t -> t.vehicleId() == p.vehicle().id()).toList(),
                    known.stream().filter(r -> r.vehicleId() == p.vehicle().id()).toList(), v.at())
                    : v.odometer();
            String notes = "Imported from " + table.name() + (p.product() == null ? "" : " · " + p.product());
            Refuel refuel;
            try {
                refuel = new Refuel(null, p.vehicle().id(), null, v.at(), odometer, estimated, v.litres(), v.amount(),
                        true, v.station(), brand, appSettings.get().defaultCountry(), notes);
            } catch (IllegalArgumentException e) {
                parsed.add(new ImportRow(p.line(), ImportRow.Status.INVALID, p.plate(), null, e.getMessage()));
                continue;
            }
            if (!seen.add(duplicateKey(refuel))) {
                parsed.add(new ImportRow(p.line(), ImportRow.Status.DUPLICATE, p.plate(), refuel,
                        "Same vehicle, time, litres and amount as one already stored"));
                continue;
            }
            known.add(refuel);
            parsed.add(new ImportRow(p.line(), ImportRow.Status.IMPORT, p.plate(), refuel,
                    estimated ? "Odometer estimated from the trips" : ""));
        }
        parsed.sort(Comparator.comparingInt(ImportRow::line));
        return parsed;
    }

    /** Stores the refuels the preview would import, in one transaction, and remembers the mapping. */
    public ImportResult importRows(TabularFile table, RefuelImportMapping mapping) {
        List<ImportRow> rows = preview(table, mapping);
        List<Refuel> toSave = rows.stream().filter(r -> r.status() == ImportRow.Status.IMPORT)
                .map(ImportRow::refuel).toList();
        refuels.saveAll(toSave);
        settings.saveAll(Map.of(MAPPING_KEY, mapping.toJson()));
        Set<String> unknown = rows.stream().filter(r -> r.status() == ImportRow.Status.UNKNOWN_PLATE)
                .map(r -> r.plate() == null ? "(empty)" : r.plate()).collect(Collectors.toCollection(TreeSet::new));
        return new ImportResult(toSave.size(), (int) toSave.stream().filter(Refuel::odometerEstimated).count(),
                count(rows, ImportRow.Status.DUPLICATE), count(rows, ImportRow.Status.NOT_FUEL), unknown,
                rows.stream().filter(r -> r.status() == ImportRow.Status.INVALID)
                        .map(r -> "Line " + r.line() + ": " + r.message()).toList());
    }

    private record Values(LocalDateTime at, BigDecimal litres, BigDecimal amount, Integer odometer, String station) {
    }

    private record Parsed(int line, String plate, Vehicle vehicle, Values values, String product) {
    }

    private static Values parse(TabularFile table, List<String> row, RefuelImportMapping mapping) {
        String dateText = cell(table, row, mapping, ImportField.DATE);
        LocalDateTime at = Inputs.dateTime(dateText, "Date");
        String timeText = cell(table, row, mapping, ImportField.TIME);
        if (timeText != null) {
            at = at.toLocalDate().atTime(Inputs.time(timeText, "Time"));
        } else if (!Inputs.hasTime(dateText)) {
            at = at.toLocalDate().atTime(LocalTime.NOON);
        }
        BigDecimal litres = Inputs.decimal(cell(table, row, mapping, ImportField.LITRES), "Litres", true);
        BigDecimal amount = Inputs.decimal(cell(table, row, mapping, ImportField.AMOUNT), "Amount", true).abs();
        Integer odometer = Inputs.integer(cell(table, row, mapping, ImportField.ODOMETER), "Odometer", false);
        if (odometer != null && odometer == 0) {
            odometer = null;
        }
        return new Values(at.truncatedTo(ChronoUnit.MINUTES), litres, amount, odometer,
                cell(table, row, mapping, ImportField.STATION));
    }

    private static String cell(TabularFile table, List<String> row, RefuelImportMapping mapping, ImportField field) {
        int column = table.column(mapping.column(field));
        if (column < 0) {
            return null;
        }
        String value = row.get(column);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isNotFuel(String product) {
        String normalised = RefuelImportMapping.normalise(product);
        return NOT_FUEL.stream().anyMatch(normalised::contains);
    }

    static String plateKey(String plate) {
        return plate.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private static String duplicateKey(Refuel refuel) {
        return refuel.vehicleId() + "|" + refuel.refuelAt().truncatedTo(ChronoUnit.MINUTES) + "|"
                + refuel.litres().stripTrailingZeros().toPlainString() + "|"
                + refuel.totalAmount().stripTrailingZeros().toPlainString();
    }

    private static String capitalised(String brand) {
        String lower = brand.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static int count(List<ImportRow> rows, ImportRow.Status status) {
        return (int) rows.stream().filter(r -> r.status() == status).count();
    }
}
