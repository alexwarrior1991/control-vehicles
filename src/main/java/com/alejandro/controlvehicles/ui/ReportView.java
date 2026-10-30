package com.alejandro.controlvehicles.ui;

import com.alejandro.controlvehicles.app.AppContext;
import com.alejandro.controlvehicles.domain.Formats;
import com.alejandro.controlvehicles.service.MonthlyReport;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.math.BigDecimal;
import java.util.List;

/**
 * The month's costs: what the refuels cost (real), what the trips should have cost by Google's distance
 * (expected) and by the odometer's distance, per vehicle and per driver, with the totals and a chart.
 */
public final class ReportView implements AppView {

    private final AppContext context;
    private final BorderPane root = new BorderPane();
    private final MonthSelector month = new MonthSelector();
    private final TableView<MonthlyReport.Line> vehicles = lineTable(true);
    private final TableView<MonthlyReport.Line> drivers = lineTable(false);
    private final GridPane totals = new GridPane();
    private final BarChart<String, Number> chart = new BarChart<>(new CategoryAxis(), new NumberAxis());
    private MonthlyReport report;

    public ReportView(AppContext context) {
        this.context = context;
        month.monthProperty().addListener((o, b, n) -> refresh());
        Button export = new Button("Export CSV");
        export.setOnAction(event -> export());
        HBox top = new HBox(10, month, export);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(0, 0, 10, 0));

        totals.setHgap(24);
        totals.setVgap(4);
        totals.setPadding(new Insets(4, 0, 10, 0));
        chart.setTitle("Fuel per vehicle: real vs expected (€)");
        chart.setAnimated(false);
        chart.setLegendVisible(true);
        chart.setMinHeight(220);

        TabPane tabs = new TabPane(new Tab("By vehicle", vehicles), new Tab("By driver", drivers));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        SplitPane split = new SplitPane(tabs, chart);
        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.setDividerPositions(0.6);
        root.setTop(new VBox(top, totals));
        root.setCenter(split);
    }

    @Override
    public String title() {
        return "Monthly report";
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        Background.run(() -> context.reports.monthly(month.month()), this::show);
    }

    private void show(MonthlyReport loaded) {
        report = loaded;
        vehicles.getItems().setAll(loaded.vehicles());
        drivers.getItems().setAll(loaded.drivers());
        MonthlyReport.Line t = loaded.total();
        totals.getChildren().clear();
        figure(0, "Real spend (refuels)", Formats.money(t.realSpend()) + " €");
        figure(1, "Expected by Google km", Formats.money(t.expectedCost()) + " €");
        figure(2, "Expected by odometer km", Formats.money(t.odometerCost()) + " €");
        figure(3, "Real − expected", Formats.money(t.costGap()) + " €");
        figure(4, "Odometer / Google km", Formats.km(t.odometerKm()) + " / " + Formats.km(t.googleKm()));
        figure(5, "Tolls declared / estimated", Formats.money(t.tollsDeclared()) + " / "
                + Formats.money(t.tollsEstimated()) + " €");
        figure(6, "Divergences", String.valueOf(t.divergences()));
        if (t.pendingTrips() > 0 || t.tripsWithoutPrice() > 0) {
            Label note = new Label(t.pendingTrips() + " trips still wait for the next one to know their odometer km; "
                    + t.tripsWithoutPrice() + " have no fuel price.");
            note.getStyleClass().add("field-hint");
            totals.add(note, 0, 2, 7, 1);
        }

        XYChart.Series<String, Number> real = new XYChart.Series<>();
        real.setName("Real (refuels)");
        XYChart.Series<String, Number> expected = new XYChart.Series<>();
        expected.setName("Expected (Google km)");
        XYChart.Series<String, Number> odometer = new XYChart.Series<>();
        odometer.setName("By odometer km");
        for (MonthlyReport.Line line : loaded.vehicles()) {
            String name = line.name().contains(" · ") ? line.name().substring(0, line.name().indexOf(" · "))
                    : line.name();
            real.getData().add(new XYChart.Data<>(name, line.realSpend()));
            expected.getData().add(new XYChart.Data<>(name, line.expectedCost()));
            odometer.getData().add(new XYChart.Data<>(name, line.odometerCost()));
        }
        chart.getData().setAll(List.of(real, expected, odometer));
    }

    private void figure(int column, String label, String value) {
        Label caption = new Label(label);
        caption.getStyleClass().add("figure-caption");
        Label number = new Label(value);
        number.getStyleClass().add("figure-value");
        totals.add(caption, column, 0);
        totals.add(number, column, 1);
    }

    private static TableView<MonthlyReport.Line> lineTable(boolean vehicle) {
        TableView<MonthlyReport.Line> table = Tables.wide(Tables.table("Nothing happened in this month."));
        javafx.scene.control.TableColumn<MonthlyReport.Line, String> name =
                Tables.text(vehicle ? "Vehicle" : "Driver", MonthlyReport.Line::name);
        name.setPrefWidth(220);
        table.getColumns().add(name);
        table.getColumns().add(Tables.number("Trips", MonthlyReport.Line::trips, String::valueOf));
        table.getColumns().add(Tables.number("Odometer km", MonthlyReport.Line::odometerKm, Formats::km));
        table.getColumns().add(Tables.number("Google km", MonthlyReport.Line::googleKm, Formats::km));
        table.getColumns().add(Tables.number("Gap km", MonthlyReport.Line::gapKm, Formats::km));
        if (vehicle) {
            table.getColumns().add(Tables.number("Nominal l/100", MonthlyReport.Line::nominalL100, Formats::decimal));
            table.getColumns().add(Tables.number("Measured l/100", MonthlyReport.Line::measuredL100,
                    Formats::decimal));
        }
        table.getColumns().add(Tables.number("Litres", MonthlyReport.Line::litresRefuelled, Formats::decimal));
        table.getColumns().add(Tables.number("Real €", MonthlyReport.Line::realSpend, ReportView::money));
        table.getColumns().add(Tables.number("Expected €", MonthlyReport.Line::expectedCost, ReportView::money));
        table.getColumns().add(Tables.number("Odometer €", MonthlyReport.Line::odometerCost, ReportView::money));
        table.getColumns().add(Tables.number("Real − expected €", MonthlyReport.Line::costGap, ReportView::money));
        table.getColumns().add(Tables.number("Tolls €", MonthlyReport.Line::tollsDeclared, ReportView::money));
        table.getColumns().add(Tables.number("Google tolls €", MonthlyReport.Line::tollsEstimated, ReportView::money));
        table.getColumns().add(Tables.number("Divergences", MonthlyReport.Line::divergences, String::valueOf));
        return table;
    }

    private static String money(BigDecimal value) {
        return Formats.money(value);
    }

    private void export() {
        if (report == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export monthly report");
        chooser.setInitialFileName("report-" + report.month() + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = chooser.showSaveDialog(root.getScene().getWindow());
        if (file != null) {
            MonthlyReport current = report;
            Background.run(() -> {
                context.csv.writeReport(current, file.toPath());
                return file;
            }, written -> Dialogs.info("Exported", written.getAbsolutePath()));
        }
    }
}
