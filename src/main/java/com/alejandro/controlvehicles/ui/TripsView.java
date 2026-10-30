package com.alejandro.controlvehicles.ui;

import com.alejandro.controlvehicles.app.AppContext;
import com.alejandro.controlvehicles.domain.Divergence;
import com.alejandro.controlvehicles.domain.Driver;
import com.alejandro.controlvehicles.domain.Formats;
import com.alejandro.controlvehicles.domain.Place;
import com.alejandro.controlvehicles.domain.Severity;
import com.alejandro.controlvehicles.domain.Vehicle;
import com.alejandro.controlvehicles.service.SaveResult;
import com.alejandro.controlvehicles.service.TripRow;
import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The trips of a month, with what the odometer and Google say next to what the driver declared. A row is
 * coloured by its worst divergence; hovering the "Divergences" cell tells them.
 */
public final class TripsView implements AppView {

    private final AppContext context;
    private final HostServices hostServices;
    private final BorderPane root = new BorderPane();
    private final MonthSelector month = new MonthSelector();
    private final ComboBox<Choice<Vehicle>> vehicleFilter = new ComboBox<>();
    private final ComboBox<Choice<Driver>> driverFilter = new ComboBox<>();
    private final CheckBox onlyDivergent = new CheckBox("Only with divergences");
    private final TableView<TripRow> table = Tables.table("No trips in this month.");
    private final Label summary = new Label();
    private final Button completePending = new Button();
    private List<TripRow> all = List.of();

    public TripsView(AppContext context, HostServices hostServices) {
        this.context = context;
        this.hostServices = hostServices;
        buildTable();

        Button add = new Button("New trip");
        add.getStyleClass().add("primary");
        add.setOnAction(event -> edit(null));
        Button edit = new Button("Edit");
        edit.setOnAction(event -> selected(this::edit));
        Button recalculate = new Button("Recalculate route and price");
        recalculate.setOnAction(event -> selected(row -> Background.run(
                () -> context.trips.recalculate(row.trip().id()), this::afterSave)));
        Button maps = new Button("Open in Google Maps");
        maps.setOnAction(event -> selected(this::openInMaps));
        Button delete = new Button("Delete");
        delete.setOnAction(event -> selected(this::delete));
        completePending.setOnAction(event -> completePending());
        HBox actions = new HBox(8, add, edit, recalculate, maps, delete, completePending);

        vehicleFilter.setPrefWidth(220);
        driverFilter.setPrefWidth(180);
        month.monthProperty().addListener((o, b, n) -> applyFilter());
        vehicleFilter.valueProperty().addListener((o, b, n) -> applyFilter());
        driverFilter.valueProperty().addListener((o, b, n) -> applyFilter());
        onlyDivergent.selectedProperty().addListener((o, b, n) -> applyFilter());
        HBox filters = new HBox(10, month, vehicleFilter, driverFilter, onlyDivergent);
        filters.setAlignment(Pos.CENTER_LEFT);

        VBox top = new VBox(10, filters, actions);
        top.setPadding(new Insets(0, 0, 10, 0));
        summary.setPadding(new Insets(8, 0, 0, 0));
        root.setTop(top);
        root.setCenter(table);
        root.setBottom(summary);
    }

    @Override
    public String title() {
        return "Trips";
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        Background.run(() -> new Object[]{context.trips.rows(), context.fleet.vehicles(), context.fleet.drivers()},
                loaded -> {
                    @SuppressWarnings("unchecked")
                    List<TripRow> rows = (List<TripRow>) loaded[0];
                    @SuppressWarnings("unchecked")
                    List<Vehicle> vehicles = (List<Vehicle>) loaded[1];
                    @SuppressWarnings("unchecked")
                    List<Driver> drivers = (List<Driver>) loaded[2];
                    all = rows;
                    long pending = rows.stream().filter(row -> row.trip().route() == null
                            || row.trip().price() == null).count();
                    completePending.setText("Complete missing routes and prices (" + pending + ")");
                    completePending.setDisable(pending == 0);
                    Choice.fill(vehicleFilter, "All vehicles", vehicles, Vehicle::label, Vehicle::id);
                    Choice.fill(driverFilter, "All drivers", drivers, Driver::name, Driver::id);
                    applyFilter();
                });
    }

    private void applyFilter() {
        YearMonth wanted = month.month();
        Vehicle vehicle = vehicleFilter.getValue() == null ? null : vehicleFilter.getValue().value();
        Driver driver = driverFilter.getValue() == null ? null : driverFilter.getValue().value();
        List<TripRow> rows = all.stream()
                .filter(row -> YearMonth.from(row.trip().date()).equals(wanted))
                .filter(row -> vehicle == null || row.trip().vehicleId() == vehicle.id())
                .filter(row -> driver == null || row.trip().driverId() == driver.id())
                .filter(row -> !onlyDivergent.isSelected() || !row.divergences().isEmpty())
                .toList();
        table.getItems().setAll(rows);
        int odometer = rows.stream().map(TripRow::odometerKm).filter(Objects::nonNull).mapToInt(Integer::intValue)
                .sum();
        BigDecimal google = rows.stream().map(TripRow::googleKm).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long pending = rows.stream().filter(TripRow::pending).count();
        int unrecorded = rows.stream().map(TripRow::unrecordedKm).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).sum();
        long divergent = rows.stream().filter(row -> !row.divergences().isEmpty()).count();
        summary.setText(String.format(Locale.ENGLISH,
                "%d trips · odometer %s km · unrecorded %s km · Google %s km · %d with divergences"
                        + " · %d waiting for the next trip",
                rows.size(), Formats.km(odometer), Formats.km(unrecorded), Formats.km(google), divergent, pending));
    }

    private void buildTable() {
        Tables.wide(table);
        TableColumn<TripRow, java.time.LocalDateTime> date = Tables.number("Date", row -> row.trip().startAt(),
                Formats::dateTime);
        date.setPrefWidth(150);
        table.getColumns().add(date);
        table.getColumns().add(Tables.text("End", row -> Formats.time(row.trip().endTime())));
        table.getColumns().add(Tables.text("Vehicle", row -> row.vehicle() == null ? "" : row.vehicle().plate()));
        TableColumn<TripRow, String> driverName = Tables.text("Driver",
                row -> row.driver() == null ? "" : row.driver().name());
        driverName.setPrefWidth(140);
        table.getColumns().add(driverName);
        TableColumn<TripRow, String> origin = Tables.text("Origin", row -> placeName(row.origin()));
        origin.setPrefWidth(150);
        table.getColumns().add(origin);
        TableColumn<TripRow, String> destination = Tables.text("Destination", row -> placeName(row.destination()));
        destination.setPrefWidth(150);
        table.getColumns().add(destination);
        table.getColumns().add(Tables.number("Start km", row -> row.trip().startOdometerKm(), Formats::km));
        table.getColumns().add(Tables.number("End km", row -> row.trip().endOdometerKm(), Formats::km));
        TableColumn<TripRow, String> odometer = Tables.text("Odometer km",
                row -> row.pending() ? "pending" : Formats.km(row.odometerKm()));
        odometer.setStyle("-fx-alignment: CENTER-RIGHT;");
        table.getColumns().add(odometer);
        table.getColumns().add(Tables.number("Unrecorded km", TripRow::unrecordedKm, Formats::km));
        table.getColumns().add(Tables.number("Google km", TripRow::googleKm, Formats::km));
        table.getColumns().add(Tables.number("Gap km", TripRow::gapKm, Formats::km));
        table.getColumns().add(Tables.text("Declared time", row -> Formats.duration(row.trip().declaredDuration())));
        table.getColumns().add(Tables.text("Google time",
                row -> row.trip().route() == null ? "" : Formats.duration(row.trip().route().duration())));
        table.getColumns().add(Tables.number("Tolls €", row -> row.trip().tollsPaid(), Formats::money));
        table.getColumns().add(Tables.text("Google tolls €", row -> row.trip().route() == null ? ""
                : switch (row.trip().route().tollStatus()) {
                    case NONE -> "none";
                    case PRICED -> Formats.money(row.trip().route().tollEstimate());
                    case UNPRICED -> "unknown";
                }));
        table.getColumns().add(Tables.text("€/l", row -> row.trip().price() == null ? "—"
                : Formats.pricePerLitre(row.trip().price().pricePerLitre())));

        TableColumn<TripRow, TripRow> flags = new TableColumn<>("Divergences");
        flags.setCellValueFactory(cell -> new javafx.beans.property.ReadOnlyObjectWrapper<>(cell.getValue()));
        flags.setSortable(false);
        flags.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(TripRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null || row.divergences().isEmpty()) {
                    setText(empty || row == null ? null : row.trip().route() == null ? "no Google route" : "");
                    setTooltip(null);
                    return;
                }
                setText(row.divergences().stream().map(d -> d.kind().label()).distinct()
                        .collect(Collectors.joining(", ")));
                Tooltip tooltip = new Tooltip(row.divergences().stream().map(Divergence::detail)
                        .collect(Collectors.joining("\n")));
                tooltip.setShowDelay(javafx.util.Duration.millis(200));
                setTooltip(tooltip);
            }
        });
        flags.setPrefWidth(320);
        table.getColumns().add(flags);

        table.setRowFactory(t -> {
            TableRow<TripRow> row = new TableRow<>() {
                @Override
                protected void updateItem(TripRow item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().removeAll("row-high", "row-warning", "row-info");
                    if (!empty && item != null) {
                        item.worstSeverity().ifPresent(severity -> getStyleClass().add(styleOf(severity)));
                    }
                }
            };
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    edit(row.getItem());
                }
            });
            return row;
        });
    }

    static String styleOf(Severity severity) {
        return switch (severity) {
            case HIGH -> "row-high";
            case WARNING -> "row-warning";
            case INFO -> "row-info";
        };
    }

    private void edit(TripRow row) {
        TripDialog.show(root, context, row == null ? null : row.trip(), row == null ? null : row.origin(),
                row == null ? null : row.destination()).ifPresent(this::afterSave);
    }

    private void completePending() {
        long count = all.stream().filter(row -> row.trip().route() == null || row.trip().price() == null).count();
        if (!Dialogs.confirm("Complete " + count + " trips?", "Google is asked for the route of each trip without one"
                + " and the price service for the price of each trip without one: up to " + count
                + " calls to Google. A manual price is kept.")) {
            return;
        }
        completePending.setDisable(true);
        completePending.setText("Completing…");
        Background.run(context.trips::completePending, result -> {
            refresh();
            List<String> lines = new java.util.ArrayList<>();
            lines.add(result.routesAdded() + " routes and " + result.pricesAdded() + " prices added to "
                    + result.trips() + " trips.");
            if (result.googleStopped()) {
                lines.add("Google stopped answering or refused the key, so it was not asked for the rest: check the"
                        + " key in Settings and try again.");
            }
            result.problems().stream().limit(8).forEach(lines::add);
            Dialogs.info("Pending trips", String.join("\n", lines));
        }, failure -> {
            refresh();
            Dialogs.error(failure);
        });
    }

    private void afterSave(SaveResult<?> result) {
        refresh();
        if (!result.warnings().isEmpty()) {
            Dialogs.warning("Saved, with something missing", String.join("\n", result.warnings()));
        }
    }

    private void delete(TripRow row) {
        if (Dialogs.confirm("Delete the trip of " + Formats.dateTime(row.trip().startAt()) + "?",
                "The distance of the vehicle's previous trip will be recalculated against the next one.")) {
            Background.run(() -> {
                context.trips.delete(row.trip().id());
                return null;
            }, done -> refresh());
        }
    }

    private void openInMaps(TripRow row) {
        if (row.origin() == null || row.destination() == null) {
            return;
        }
        StringBuilder url = new StringBuilder("https://www.google.com/maps/dir/?api=1&travelmode=driving");
        url.append("&origin=").append(encode(row.origin().label()));
        url.append("&destination=").append(encode(row.destination().label()));
        if (row.origin().googlePlaceId() != null) {
            url.append("&origin_place_id=").append(encode(row.origin().googlePlaceId()));
        }
        if (row.destination().googlePlaceId() != null) {
            url.append("&destination_place_id=").append(encode(row.destination().googlePlaceId()));
        }
        hostServices.showDocument(url.toString());
    }

    private void selected(Consumer<TripRow> action) {
        TripRow row = table.getSelectionModel().getSelectedItem();
        if (row != null) {
            action.accept(row);
        }
    }

    private static String placeName(Place place) {
        return place == null ? "" : place.name();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
