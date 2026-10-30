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
import javafx.scene.layout.FlowPane;
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
    private final FlowPane figures = new FlowPane(22, 8);
    private final Label totalsNote = new Label();
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

        figures.setPadding(new Insets(4, 0, 4, 0));
        totalsNote.getStyleClass().add("field-hint");
        totalsNote.setWrapText(true);
        chart.setTitle("Fuel per vehicle: real vs expected (€)");
        chart.setAnimated(false);
        chart.setLegendVisible(true);
        chart.setMinHeight(220);

        TabPane tabs = new TabPane(new Tab("By vehicle", vehicles), new Tab("By driver", drivers));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        SplitPane split = new SplitPane(tabs, chart);
        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.setDividerPositions(0.58);
        VBox header = new VBox(4, top, figures, totalsNote);
        header.setPadding(new Insets(0, 0, 10, 0));
        root.setTop(header);
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
        figures.getChildren().clear();
        figure(0, "Real spend (refuels)", Formats.money(t.realSpend()) + " €");
        figure(1, "Expected by Google km", Formats.money(t.expectedCost()) + " €");
        figure(2, "Expected by odometer km", Formats.money(t.odometerCost()) + " €");
        figure(3, "Real − expected", Formats.money(t.costGap()) + " €");
        figure(4, "Odometer / Google km", Formats.km(t.odometerKm()) + " / " + Formats.km(t.googleKm()));
        figure(5, "Tolls declared / estimated", Formats.money(t.tollsDeclared()) + " / "
                + Formats.money(t.tollsEstimated()) + " €");
        figure(6, "Unrecorded km", Formats.km(t.unrecordedKm()));
        figure(7, "Divergences", String.valueOf(t.divergences()));
        totalsNote.setText(t.pendingTrips() > 0 || t.tripsWithoutPrice() > 0
                ? t.pendingTrips() + " trips still wait for the next one to know their odometer km; "
                + t.tripsWithoutPrice() + " have no fuel price." : "");

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

    private void figure(int order, String label, String value) {
        Label caption = new Label(label);
        caption.getStyleClass().add("figure-caption");
        Label number = new Label(value);
        number.getStyleClass().add("figure-value");
        figures.getChildren().add(new VBox(2, caption, number));
    }

    private static TableView<MonthlyReport.Line> lineTable(boolean vehicle) {
        TableView<MonthlyReport.Line> table = Tables.table("Nothing happened in this month.");
        table.getColumns().add(Tables.width(Tables.text(vehicle ? "Vehicle" : "Driver", MonthlyReport.Line::name),
                vehicle ? 170 : 140));
        table.getColumns().add(Tables.width(Tables.number("Trips", MonthlyReport.Line::trips, String::valueOf), 60));
        table.getColumns().add(Tables.group("Km",
                Tables.width(Tables.number("Odometer", MonthlyReport.Line::odometerKm, Formats::km), 85),
                Tables.width(Tables.number("Unrec.", MonthlyReport.Line::unrecordedKm, Formats::km), 70),
                Tables.width(Tables.number("Google", MonthlyReport.Line::googleKm, Formats::km), 75),
                Tables.width(Tables.number("Gap", MonthlyReport.Line::gapKm, Formats::km), 70)));
        if (vehicle) {
            table.getColumns().add(Tables.group("l/100 km",
                    Tables.width(Tables.number("Nominal", MonthlyReport.Line::nominalL100, Formats::decimal), 75),
                    Tables.width(Tables.number("Measured", MonthlyReport.Line::measuredL100, Formats::decimal), 85)));
        }
        table.getColumns().add(Tables.width(Tables.number("Litres", MonthlyReport.Line::litresRefuelled,
                Formats::decimal), 70));
        table.getColumns().add(Tables.group("Fuel €",
                Tables.width(Tables.number("Real", MonthlyReport.Line::realSpend, ReportView::money), 80),
                Tables.width(Tables.number("Expected", MonthlyReport.Line::expectedCost, ReportView::money), 85),
                Tables.width(Tables.number("By odo.", MonthlyReport.Line::odometerCost, ReportView::money), 80),
                Tables.width(Tables.number("Gap", MonthlyReport.Line::costGap, ReportView::money), 75)));
        table.getColumns().add(Tables.group("Tolls €",
                Tables.width(Tables.number("Paid", MonthlyReport.Line::tollsDeclared, ReportView::money), 65),
                Tables.width(Tables.number("Google", MonthlyReport.Line::tollsEstimated, ReportView::money), 70)));
        table.getColumns().add(Tables.width(Tables.number("Issues", MonthlyReport.Line::divergences,
                String::valueOf), 70));
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
