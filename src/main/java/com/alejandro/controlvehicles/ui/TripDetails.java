package com.alejandro.controlvehicles.ui;

import com.alejandro.controlvehicles.domain.CostCalculator;
import com.alejandro.controlvehicles.domain.Divergence;
import com.alejandro.controlvehicles.domain.Formats;
import com.alejandro.controlvehicles.domain.RouteEstimate;
import com.alejandro.controlvehicles.domain.Trip;
import com.alejandro.controlvehicles.service.TripRow;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;

/**
 * Everything about the selected trip, under the list: what the list only abbreviates (both odometers, the
 * unrecorded km, tolls, the price and where it came from, the expected cost) and each divergence in full.
 */
final class TripDetails extends ScrollPane {

    private final VBox content = new VBox(8);

    TripDetails() {
        content.setPadding(new Insets(10, 12, 10, 12));
        content.getStyleClass().add("details");
        setContent(content);
        setFitToWidth(true);
        show(null);
    }

    void show(TripRow row) {
        content.getChildren().clear();
        if (row == null) {
            Label empty = new Label("Select a trip to see all its figures and its divergences here.");
            empty.getStyleClass().add("field-hint");
            content.getChildren().add(empty);
            return;
        }
        Trip trip = row.trip();
        RouteEstimate route = trip.route();
        Label title = new Label(name(row.origin() == null ? null : row.origin().name()) + "  →  "
                + name(row.destination() == null ? null : row.destination().name()));
        title.getStyleClass().add("section-title");
        Label subtitle = new Label(Formats.date(trip.date()) + "  " + Formats.time(trip.startTime()) + "–"
                + Formats.time(trip.endTime()) + "  ·  " + (row.vehicle() == null ? "?" : row.vehicle().label())
                + "  ·  " + (row.driver() == null ? "?" : row.driver().name()));
        subtitle.getStyleClass().add("field-hint");

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(4);
        for (int i = 0; i < 3; i++) {
            ColumnConstraints label = new ColumnConstraints();
            label.setMinWidth(110);
            ColumnConstraints value = new ColumnConstraints();
            value.setHgrow(Priority.SOMETIMES);
            value.setMinWidth(120);
            grid.getColumnConstraints().addAll(label, value);
        }
        put(grid, 0, 0, "Start km", Formats.km(trip.startOdometerKm()));
        put(grid, 0, 1, "End km", trip.endOdometerKm() == null ? "not noted" : Formats.km(trip.endOdometerKm()));
        put(grid, 0, 2, "Odometer km", row.pending() ? "pending (next trip)" : Formats.km(row.odometerKm()));
        put(grid, 1, 0, "Unrecorded km", row.unrecordedKm() == null ? "—" : Formats.km(row.unrecordedKm()));
        put(grid, 1, 1, "Google km", route == null ? "no route" : Formats.km(route.distanceKm()));
        put(grid, 1, 2, "Gap km", row.gapKm() == null ? "—" : signed(row.gapKm()));
        put(grid, 2, 0, "Declared time", Formats.duration(trip.declaredDuration()));
        put(grid, 2, 1, "Google time", route == null ? "—" : Formats.duration(route.duration()));
        put(grid, 2, 2, "Tolls paid", Formats.money(trip.tollsPaid()) + " €");
        put(grid, 3, 0, "Google tolls", route == null ? "—" : switch (route.tollStatus()) {
            case NONE -> "none on the route";
            case PRICED -> Formats.money(route.tollEstimate()) + " €";
            case UNPRICED -> "unknown";
        });
        put(grid, 3, 1, "Fuel price", trip.price() == null ? "none"
                : Formats.pricePerLitre(trip.price().pricePerLitre()) + " €/l");
        BigDecimal nominal = row.vehicle() == null ? null : row.vehicle().nominalConsumptionL100();
        put(grid, 3, 2, "Expected fuel", trip.price() == null || route == null || nominal == null ? "—"
                : Formats.money(CostCalculator.fuelCost(route.distanceKm(), nominal, trip.price().pricePerLitre()))
                + " € by Google km");
        content.getChildren().addAll(title, subtitle, grid);
        if (trip.price() != null) {
            content.getChildren().add(hint("Price source: " + trip.price().source()));
        }
        if (trip.notes() != null) {
            content.getChildren().add(hint("Notes: " + trip.notes()));
        }

        Label divergenceTitle = new Label(row.divergences().isEmpty() ? "No divergences"
                : row.divergences().size() + (row.divergences().size() == 1 ? " divergence" : " divergences"));
        divergenceTitle.getStyleClass().add("subsection-title");
        content.getChildren().add(divergenceTitle);
        for (Divergence divergence : row.divergences()) {
            Label line = new Label(divergence.severity() + " · " + divergence.kind().label() + ": "
                    + divergence.detail());
            line.setWrapText(true);
            line.getStyleClass().addAll("divergence-line", TripsView.styleOf(divergence.severity()));
            line.setMaxWidth(Double.MAX_VALUE);
            content.getChildren().add(line);
        }
    }

    private static void put(GridPane grid, int row, int pair, String label, String value) {
        Label caption = new Label(label);
        caption.getStyleClass().add("figure-caption");
        Label text = new Label(value);
        text.getStyleClass().add("detail-value");
        grid.add(caption, pair * 2, row);
        grid.add(text, pair * 2 + 1, row);
    }

    private static Label hint(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-hint");
        label.setWrapText(true);
        return label;
    }

    private static String signed(BigDecimal value) {
        return (value.signum() > 0 ? "+" : "") + Formats.km(value);
    }

    private static String name(String text) {
        return text == null ? "?" : text;
    }
}
