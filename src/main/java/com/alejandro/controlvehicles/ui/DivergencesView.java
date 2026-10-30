package com.alejandro.controlvehicles.ui;

import com.alejandro.controlvehicles.app.AppContext;
import com.alejandro.controlvehicles.domain.Divergence;
import com.alejandro.controlvehicles.domain.DivergenceKind;
import com.alejandro.controlvehicles.domain.Driver;
import com.alejandro.controlvehicles.domain.Formats;
import com.alejandro.controlvehicles.domain.Severity;
import com.alejandro.controlvehicles.domain.Vehicle;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Every divergence of a month, the worst first, with the filter to look at one vehicle, driver or kind. */
public final class DivergencesView implements AppView {

    private final AppContext context;
    private final BorderPane root = new BorderPane();
    private final MonthSelector month = new MonthSelector();
    private final ComboBox<Choice<Vehicle>> vehicleFilter = new ComboBox<>();
    private final ComboBox<Choice<Driver>> driverFilter = new ComboBox<>();
    private final ComboBox<Choice<DivergenceKind>> kindFilter = new ComboBox<>();
    private final TableView<Divergence> table = Tables.table("No divergences in this month.");
    private final Label summary = new Label();
    private List<Divergence> all = List.of();
    private Map<Long, Vehicle> vehicles = Map.of();
    private Map<Long, Driver> drivers = Map.of();

    public DivergencesView(AppContext context) {
        this.context = context;
        table.getColumns().add(Tables.number("Date", Divergence::date, Formats::date));
        table.getColumns().add(Tables.number("Severity", Divergence::severity, Severity::name));
        javafx.scene.control.TableColumn<Divergence, String> kind = Tables.text("Kind", d -> d.kind().label());
        kind.setPrefWidth(230);
        table.getColumns().add(kind);
        table.getColumns().add(Tables.text("Vehicle", this::vehicleName));
        table.getColumns().add(Tables.text("Driver", this::driverName));
        javafx.scene.control.TableColumn<Divergence, String> detail = Tables.text("Detail", Divergence::detail);
        detail.setPrefWidth(520);
        table.getColumns().add(detail);
        table.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(Divergence item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("row-high", "row-warning", "row-info");
                if (!empty && item != null) {
                    getStyleClass().add(TripsView.styleOf(item.severity()));
                }
            }
        });

        Choice.fill(kindFilter, "All kinds", Arrays.asList(DivergenceKind.values()), DivergenceKind::label, k -> k);
        vehicleFilter.setPrefWidth(220);
        driverFilter.setPrefWidth(180);
        month.monthProperty().addListener((o, b, n) -> applyFilter());
        vehicleFilter.valueProperty().addListener((o, b, n) -> applyFilter());
        driverFilter.valueProperty().addListener((o, b, n) -> applyFilter());
        kindFilter.valueProperty().addListener((o, b, n) -> applyFilter());
        Button export = new Button("Export CSV");
        export.setOnAction(event -> export());
        HBox filters = new HBox(10, month, vehicleFilter, driverFilter, kindFilter, export);
        filters.setAlignment(Pos.CENTER_LEFT);
        Label explanation = new Label("Odometer vs Google: the odometer distance of a trip runs until the vehicle's"
                + " next trip, so any use nobody registered in between shows up here.");
        explanation.getStyleClass().add("field-hint");
        explanation.setWrapText(true);
        VBox top = new VBox(8, filters, explanation);
        top.setPadding(new Insets(0, 0, 10, 0));
        summary.setPadding(new Insets(8, 0, 0, 0));
        root.setTop(top);
        root.setCenter(table);
        root.setBottom(summary);
    }

    @Override
    public String title() {
        return "Divergences";
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        Background.run(() -> new Object[]{context.analysis.analyse().divergences(), context.fleet.vehicles(),
                context.fleet.drivers()}, loaded -> {
            @SuppressWarnings("unchecked")
            List<Divergence> divergences = (List<Divergence>) loaded[0];
            @SuppressWarnings("unchecked")
            List<Vehicle> vehicleList = (List<Vehicle>) loaded[1];
            @SuppressWarnings("unchecked")
            List<Driver> driverList = (List<Driver>) loaded[2];
            all = divergences;
            vehicles = vehicleList.stream().collect(Collectors.toMap(Vehicle::id, v -> v));
            drivers = driverList.stream().collect(Collectors.toMap(Driver::id, d -> d));
            Choice.fill(vehicleFilter, "All vehicles", vehicleList, Vehicle::label, Vehicle::id);
            Choice.fill(driverFilter, "All drivers", driverList, Driver::name, Driver::id);
            applyFilter();
        });
    }

    private void applyFilter() {
        YearMonth wanted = month.month();
        Vehicle vehicle = vehicleFilter.getValue() == null ? null : vehicleFilter.getValue().value();
        Driver driver = driverFilter.getValue() == null ? null : driverFilter.getValue().value();
        DivergenceKind kind = kindFilter.getValue() == null ? null : kindFilter.getValue().value();
        List<Divergence> rows = all.stream()
                .filter(d -> YearMonth.from(d.date()).equals(wanted))
                .filter(d -> vehicle == null || vehicle.id().equals(d.vehicleId()))
                .filter(d -> driver == null || driver.id().equals(d.driverId()))
                .filter(d -> kind == null || d.kind() == kind)
                .sorted(Comparator.comparing(Divergence::severity).reversed().thenComparing(Divergence::date))
                .toList();
        table.getItems().setAll(rows);
        Map<Severity, Long> counts = rows.stream().collect(Collectors.groupingBy(Divergence::severity,
                Collectors.counting()));
        summary.setText(rows.size() + " divergences · " + counts.getOrDefault(Severity.HIGH, 0L) + " high · "
                + counts.getOrDefault(Severity.WARNING, 0L) + " warning · " + counts.getOrDefault(Severity.INFO, 0L)
                + " info");
    }

    private void export() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export divergences");
        chooser.setInitialFileName("divergences-" + month.month() + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = chooser.showSaveDialog(root.getScene().getWindow());
        if (file != null) {
            List<Divergence> rows = List.copyOf(table.getItems());
            Background.run(() -> {
                context.csv.writeDivergences(rows, this::vehicleName, this::driverName, file.toPath());
                return file;
            }, written -> Dialogs.info("Exported", written.getAbsolutePath()));
        }
    }

    private String vehicleName(Divergence divergence) {
        Vehicle vehicle = divergence.vehicleId() == null ? null : vehicles.get(divergence.vehicleId());
        return vehicle == null ? "" : vehicle.plate();
    }

    private String driverName(Divergence divergence) {
        Driver driver = divergence.driverId() == null ? null : drivers.get(divergence.driverId());
        return driver == null ? "" : driver.name();
    }
}
