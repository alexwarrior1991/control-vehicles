package com.alejandro.controlvehicles.ui;

import com.alejandro.controlvehicles.app.AppContext;
import com.alejandro.controlvehicles.domain.ConsumptionSegment;
import com.alejandro.controlvehicles.domain.Country;
import com.alejandro.controlvehicles.domain.Driver;
import com.alejandro.controlvehicles.domain.Formats;
import com.alejandro.controlvehicles.domain.Refuel;
import com.alejandro.controlvehicles.domain.Vehicle;
import com.alejandro.controlvehicles.service.Inputs;
import com.alejandro.controlvehicles.service.ValidationException;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Refuels, the real spend, and below them the consumption the vehicle really had between two full tanks
 * against its nominal one.
 */
public final class RefuelsView implements AppView {

    private final AppContext context;
    private final BorderPane root = new BorderPane();
    private final MonthSelector month = new MonthSelector();
    private final ComboBox<Choice<Vehicle>> vehicleFilter = new ComboBox<>();
    private final TableView<Refuel> table = Tables.table("No refuels in this month.");
    private final TableView<ConsumptionSegment> segments =
            Tables.table("Choose a vehicle: two full-tank refuels are needed to measure its consumption.");
    private final Label summary = new Label();
    private List<Refuel> all = List.of();
    private Map<Long, Vehicle> vehicles = Map.of();
    private Map<Long, Driver> drivers = Map.of();

    public RefuelsView(AppContext context) {
        this.context = context;
        javafx.scene.control.TableColumn<Refuel, java.time.LocalDateTime> date =
                Tables.number("Date", Refuel::refuelAt, Formats::dateTime);
        date.setPrefWidth(150);
        table.getColumns().add(date);
        table.getColumns().add(Tables.text("Vehicle", r -> vehicleName(r.vehicleId())));
        table.getColumns().add(Tables.text("Driver", r -> r.driverId() == null ? ""
                : drivers.containsKey(r.driverId()) ? drivers.get(r.driverId()).name() : "?"));
        TableColumn<Refuel, String> odometerColumn = Tables.text("Odometer km",
                r -> (r.odometerEstimated() ? "~" : "") + Formats.km(r.odometerKm())
                        + (r.odometerEstimated() ? " (est.)" : ""));
        odometerColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        odometerColumn.setPrefWidth(150);
        table.getColumns().add(odometerColumn);
        table.getColumns().add(Tables.number("Litres", Refuel::litres, Formats::decimal));
        table.getColumns().add(Tables.number("Amount €", Refuel::totalAmount, Formats::money));
        table.getColumns().add(Tables.number("€/l", Refuel::pricePerLitre, Formats::pricePerLitre));
        table.getColumns().add(Tables.width(Tables.text("Full", r -> r.fullTank() ? "Yes" : "No"), 60));
        table.getColumns().add(Tables.width(Tables.text("Station", r -> join(r.brand(), r.station())
                + (r.country() == null ? "" : " (" + r.country().name() + ")")), 200));
        Tables.onDoubleClick(table, this::edit);

        segments.getColumns().add(Tables.number("From", s -> s.from().refuelAt(), Formats::dateTime));
        segments.getColumns().add(Tables.number("To", s -> s.to().refuelAt(), Formats::dateTime));
        segments.getColumns().add(Tables.number("Km", ConsumptionSegment::km, Formats::km));
        segments.getColumns().add(Tables.number("Litres", ConsumptionSegment::litres, Formats::decimal));
        segments.getColumns().add(Tables.number("Measured l/100", ConsumptionSegment::litresPer100Km,
                Formats::decimal));
        segments.getColumns().add(Tables.text("Nominal l/100",
                s -> vehicles.containsKey(s.vehicleId())
                        ? Formats.decimal(vehicles.get(s.vehicleId()).nominalConsumptionL100()) : ""));

        Button add = new Button("New refuel");
        add.getStyleClass().add("primary");
        add.setOnAction(event -> edit(null));
        Button edit = new Button("Edit");
        edit.setOnAction(event -> selected(this::edit));
        Button delete = new Button("Delete");
        delete.setOnAction(event -> selected(this::delete));
        Button importStatement = new Button("Import Galp Frota statement…");
        importStatement.setOnAction(event -> RefuelImportDialog.show(root, context).ifPresent(result -> {
            refresh();
            Dialogs.info(result.imported() + " refuels imported", importSummary(result));
        }));
        vehicleFilter.setPrefWidth(240);
        month.monthProperty().addListener((o, b, n) -> applyFilter());
        vehicleFilter.valueProperty().addListener((o, b, n) -> applyFilter());
        HBox filters = new HBox(10, month, vehicleFilter);
        filters.setAlignment(Pos.CENTER_LEFT);
        VBox top = new VBox(10, filters, new HBox(8, add, edit, delete, importStatement));
        top.setPadding(new Insets(0, 0, 10, 0));

        Label consumptionTitle = new Label("Measured consumption (full tank to full tank)");
        consumptionTitle.getStyleClass().add("section-title");
        VBox lower = new VBox(6, consumptionTitle, segments);
        VBox upper = new VBox(6, table, summary);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        VBox.setVgrow(segments, javafx.scene.layout.Priority.ALWAYS);
        SplitPane split = new SplitPane(upper, lower);
        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.setDividerPositions(0.62);
        root.setTop(top);
        root.setCenter(split);
    }

    @Override
    public String title() {
        return "Refuels";
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        Background.run(() -> new Object[]{context.refuels.all(), context.fleet.vehicles(), context.fleet.drivers()},
                loaded -> {
                    @SuppressWarnings("unchecked")
                    List<Refuel> refuels = (List<Refuel>) loaded[0];
                    @SuppressWarnings("unchecked")
                    List<Vehicle> vehicleList = (List<Vehicle>) loaded[1];
                    @SuppressWarnings("unchecked")
                    List<Driver> driverList = (List<Driver>) loaded[2];
                    all = refuels;
                    vehicles = vehicleList.stream().collect(Collectors.toMap(Vehicle::id, v -> v));
                    drivers = driverList.stream().collect(Collectors.toMap(Driver::id, d -> d));
                    Choice.fill(vehicleFilter, "All vehicles", vehicleList, Vehicle::label, Vehicle::id);
                    applyFilter();
                });
    }

    private void applyFilter() {
        YearMonth wanted = month.month();
        Vehicle vehicle = vehicleFilter.getValue() == null ? null : vehicleFilter.getValue().value();
        List<Refuel> rows = all.stream()
                .filter(r -> YearMonth.from(r.refuelAt()).equals(wanted))
                .filter(r -> vehicle == null || r.vehicleId() == vehicle.id())
                .toList();
        table.getItems().setAll(rows);
        BigDecimal litres = rows.stream().map(Refuel::litres).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal amount = rows.stream().map(Refuel::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        summary.setText(rows.size() + " refuels · " + Formats.decimal(litres) + " l · " + Formats.money(amount) + " €");
        if (vehicle == null) {
            segments.getItems().clear();
        } else {
            Background.run(() -> context.refuels.consumption(vehicle.id()),
                    list -> segments.getItems().setAll(list.reversed()));
        }
    }

    private void edit(Refuel refuel) {
        List<Vehicle> choosable = new ArrayList<>(context.fleet.activeVehicles());
        List<Driver> people = new ArrayList<>(context.fleet.activeDrivers());
        if (refuel != null) {
            context.fleet.vehicles().stream().filter(v -> v.id() == refuel.vehicleId() && !choosable.contains(v))
                    .forEach(choosable::add);
            context.fleet.drivers().stream()
                    .filter(d -> refuel.driverId() != null && d.id() == refuel.driverId() && !people.contains(d))
                    .forEach(people::add);
        }
        if (choosable.isEmpty()) {
            Dialogs.info("Nothing to choose yet", "Add a vehicle first.");
            return;
        }
        ComboBox<Vehicle> vehicle = new ComboBox<>();
        vehicle.getItems().setAll(choosable);
        ComboBox<Choice<Driver>> driver = new ComboBox<>();
        Choice.fill(driver, "(not recorded)", people, Driver::name, Driver::id);
        DatePicker date = Dates.picker(refuel == null ? LocalDate.now() : refuel.refuelAt().toLocalDate());
        TextField time = new TextField(refuel == null ? "" : Formats.time(refuel.refuelAt().toLocalTime()));
        time.setPromptText("12:00");
        TextField odometer = new TextField(refuel == null ? "" : String.valueOf(refuel.odometerKm()));
        CheckBox estimated = new CheckBox("Estimated (not on the receipt)");
        estimated.setSelected(refuel != null && refuel.odometerEstimated());
        odometer.textProperty().addListener((o, b, n) -> {
            if (odometer.isFocused()) {
                estimated.setSelected(false);
            }
        });
        Label estimatedHint = new Label("An estimated odometer does not count for consumption or km checks.");
        estimatedHint.getStyleClass().add("field-hint");
        TextField litres = new TextField(refuel == null ? "" : VehiclesView.plain(refuel.litres()));
        TextField amount = new TextField(refuel == null ? "" : VehiclesView.plain(refuel.totalAmount()));
        Label pricePerLitre = new Label();
        pricePerLitre.getStyleClass().add("field-hint");
        Runnable updatePrice = () -> {
            try {
                BigDecimal l = Inputs.decimal(litres.getText(), "Litres", true);
                BigDecimal a = Inputs.decimal(amount.getText(), "Amount", true);
                pricePerLitre.setText(l.signum() > 0 ? Formats.pricePerLitre(
                        a.divide(l, 3, java.math.RoundingMode.HALF_UP)) + " €/l" : "");
            } catch (ValidationException e) {
                pricePerLitre.setText("");
            }
        };
        litres.textProperty().addListener((o, b, n) -> updatePrice.run());
        amount.textProperty().addListener((o, b, n) -> updatePrice.run());
        updatePrice.run();
        CheckBox full = new CheckBox("Tank filled up");
        full.setSelected(refuel == null || refuel.fullTank());
        TextField brand = new TextField(refuel == null ? "Galp" : DriversView.nullToEmpty(refuel.brand()));
        TextField station = new TextField(refuel == null ? "" : DriversView.nullToEmpty(refuel.station()));
        station.setPromptText("Town or station");
        ComboBox<Country> country = new ComboBox<>();
        country.getItems().setAll(Country.values());
        country.setValue(refuel == null || refuel.country() == null ? context.settings.load().defaultCountry()
                : refuel.country());
        TextArea notes = new TextArea(refuel == null ? "" : DriversView.nullToEmpty(refuel.notes()));
        notes.setPrefRowCount(2);
        if (refuel != null) {
            choosable.stream().filter(v -> v.id() == refuel.vehicleId()).findFirst().ifPresent(vehicle::setValue);
            driver.getItems().stream().filter(c -> c.value() != null && c.value().id().equals(refuel.driverId()))
                    .findFirst().ifPresent(driver::setValue);
        } else {
            vehicle.setValue(choosable.getFirst());
        }
        Label fullHint = new Label("Untick it for a partial refuel: the consumption is measured between full tanks.");
        fullHint.getStyleClass().add("field-hint");
        fullHint.setWrapText(true);
        fullHint.setMaxWidth(420);

        FormGrid form = new FormGrid().add("Vehicle *", vehicle).add("Driver", driver).add("Date *", date)
                .add("Time", time)
                .add("Odometer (km) *", new VBox(2, odometer, estimated, estimatedHint)).add("Litres *", litres)
                .add("Amount paid (€) *", new VBox(2, amount, pricePerLitre))
                .add("", new VBox(2, full, fullHint)).add("Brand", brand).add("Station", station)
                .add("Country", country).add("Notes", notes);
        EditorDialog.show(root, refuel == null ? "New refuel" : "Edit refuel", form, () -> {
            if (vehicle.getValue() == null) {
                throw new ValidationException("Choose a vehicle");
            }
            if (date.getValue() == null) {
                throw new ValidationException("Date is required");
            }
            LocalTime at = time.getText().isBlank() ? LocalTime.NOON : Inputs.time(time.getText(), "Time");
            Driver who = driver.getValue() == null ? null : driver.getValue().value();
            Refuel edited = new Refuel(refuel == null ? null : refuel.id(), vehicle.getValue().id(),
                    who == null ? null : who.id(), date.getValue().atTime(at),
                    Inputs.integer(odometer.getText(), "Odometer", true), estimated.isSelected(),
                    Inputs.decimal(litres.getText(), "Litres", true),
                    Inputs.decimal(amount.getText(), "Amount", true), full.isSelected(), station.getText(),
                    brand.getText(), country.getValue(), notes.getText());
            Callable<Refuel> save = () -> context.refuels.save(edited);
            return save;
        }).ifPresent(saved -> refresh());
    }

    static String importSummary(com.alejandro.controlvehicles.service.imports.ImportResult result) {
        List<String> lines = new ArrayList<>();
        if (result.estimatedOdometers() > 0) {
            lines.add(result.estimatedOdometers() + " with the odometer estimated from the trips: correct them in the"
                    + " list when the real reading is known.");
        }
        if (result.duplicates() > 0) {
            lines.add(result.duplicates() + " already imported, left out.");
        }
        if (result.notFuel() > 0) {
            lines.add(result.notFuel() + " lines that are not fuel, left out.");
        }
        if (!result.unknownPlates().isEmpty()) {
            lines.add("Plates without a vehicle (add them in Vehicles and import again): "
                    + String.join(", ", result.unknownPlates()));
        }
        if (!result.invalid().isEmpty()) {
            lines.add(result.invalid().size() + " lines could not be read:");
            result.invalid().stream().limit(10).forEach(lines::add);
        }
        lines.add("Every imported refuel is marked as a full tank: untick the ones that were not.");
        return String.join("\n", lines);
    }

    private void delete(Refuel refuel) {
        if (Dialogs.confirm("Delete the refuel of " + Formats.dateTime(refuel.refuelAt()) + "?", "")) {
            Background.run(() -> {
                context.refuels.delete(refuel.id());
                return null;
            }, done -> refresh());
        }
    }

    private void selected(Consumer<Refuel> action) {
        Refuel refuel = table.getSelectionModel().getSelectedItem();
        if (refuel != null) {
            action.accept(refuel);
        }
    }

    private String vehicleName(long id) {
        return vehicles.containsKey(id) ? vehicles.get(id).plate() : "?";
    }

    private static String join(String a, String b) {
        if (a == null) {
            return b == null ? "" : b;
        }
        return b == null ? a : a + " · " + b;
    }
}
