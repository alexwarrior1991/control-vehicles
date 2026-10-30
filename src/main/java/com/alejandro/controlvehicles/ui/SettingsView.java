package com.alejandro.controlvehicles.ui;

import com.alejandro.controlvehicles.app.AppContext;
import com.alejandro.controlvehicles.domain.Country;
import com.alejandro.controlvehicles.domain.DivergenceThresholds;
import com.alejandro.controlvehicles.service.AppSettings;
import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The Google key (local, never exported), the divergence rules and the price rules (in the database, so they
 * travel with it), and the database itself: export it to hand it over, import one received.
 */
public final class SettingsView implements AppView {

    private final AppContext context;
    private final HostServices hostServices;
    private final Runnable onDatabaseReplaced;
    private final VBox content = new VBox(14);
    private final PasswordField apiKey = new PasswordField();
    private final Label keyStatus = new Label();
    private final TextField distancePercent = new TextField();
    private final TextField distanceMinKm = new TextField();
    private final TextField durationPercent = new TextField();
    private final TextField durationMinMinutes = new TextField();
    private final TextField consumptionPercent = new TextField();
    private final TextField tollTolerance = new TextField();
    private final TextField radius = new TextField();
    private final TextField brand = new TextField();
    private final ComboBox<Country> defaultCountry = new ComboBox<>();
    private final Label rulesStatus = new Label();

    public SettingsView(AppContext context, HostServices hostServices, Runnable onDatabaseReplaced) {
        this.context = context;
        this.hostServices = hostServices;
        this.onDatabaseReplaced = onDatabaseReplaced;
        content.setPadding(new Insets(0, 0, 20, 0));
        defaultCountry.getItems().setAll(Country.values());

        Button saveKey = new Button("Save key");
        saveKey.getStyleClass().add("primary");
        saveKey.setOnAction(event -> Background.run(() -> {
            context.settings.saveGoogleApiKey(apiKey.getText());
            return null;
        }, done -> keyStatus.setText("Saved.")));
        Button testKey = new Button("Test the key");
        testKey.setOnAction(event -> testKey());
        keyStatus.getStyleClass().add("field-hint");
        Label keyHelp = hint("Enable «Places API (New)» and «Routes API» for the key in Google Cloud. It is stored"
                + " only on this computer, never in the exported database.");
        FormGrid google = new FormGrid().add("API key", apiKey).add("", new HBox(8, saveKey, testKey))
                .add("", keyStatus).addWide(keyHelp);

        Button saveRules = new Button("Save rules");
        saveRules.getStyleClass().add("primary");
        saveRules.setOnAction(event -> saveRules());
        rulesStatus.getStyleClass().add("field-hint");
        FormGrid rules = new FormGrid()
                .add("Distance gap above (%)", distancePercent)
                .add("… and above (km)", distanceMinKm)
                .add("Duration gap above (%)", durationPercent)
                .add("… and above (min)", durationMinMinutes)
                .add("Consumption gap above (%)", consumptionPercent)
                .add("Toll tolerance (€)", tollTolerance)
                .add("Price search radius (km)", radius)
                .add("Preferred brand", brand)
                .add("Default country", defaultCountry)
                .add("", new HBox(8, saveRules, rulesStatus));

        Label path = new Label(context.database.file().toString());
        path.setWrapText(true);
        Button export = new Button("Export database…");
        export.getStyleClass().add("primary");
        export.setOnAction(event -> exportDatabase());
        Button importButton = new Button("Import database…");
        importButton.setOnAction(event -> importDatabase());
        Button folder = new Button("Open data folder");
        folder.setOnAction(event -> hostServices.showDocument(context.database.file().getParent().toUri().toString()));
        FormGrid database = new FormGrid().add("File", path).add("", new HBox(8, export, importButton, folder))
                .addWide(hint("The export is one .db file with everything (drivers, vehicles, trips, refuels, prices and"
                        + " rules). Importing replaces the current data; a backup of it is left next to it."));

        content.getChildren().addAll(section("Google"), google, section("Rules"), rules, section("Database"),
                database);
    }

    @Override
    public String title() {
        return "Settings";
    }

    @Override
    public Node node() {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        return scroll;
    }

    @Override
    public void refresh() {
        Background.run(() -> new Object[]{context.settings.googleApiKey(), context.settings.load()}, loaded -> {
            apiKey.setText((String) loaded[0]);
            AppSettings settings = (AppSettings) loaded[1];
            DivergenceThresholds t = settings.thresholds();
            distancePercent.setText(String.valueOf(t.distancePercent()));
            distanceMinKm.setText(String.valueOf(t.distanceMinKm()));
            durationPercent.setText(String.valueOf(t.durationPercent()));
            durationMinMinutes.setText(String.valueOf(t.durationMinMinutes()));
            consumptionPercent.setText(String.valueOf(t.consumptionPercent()));
            tollTolerance.setText(t.tollToleranceEur().toPlainString());
            radius.setText(String.valueOf(settings.priceRadiusKm()));
            brand.setText(settings.preferredBrand() == null ? "" : settings.preferredBrand());
            defaultCountry.setValue(settings.defaultCountry());
            keyStatus.setText("");
            rulesStatus.setText("");
        });
    }

    private void testKey() {
        String key = apiKey.getText();
        keyStatus.setText("Testing…");
        Background.run(() -> {
            context.settings.saveGoogleApiKey(key);
            return context.places.searchGoogle("Madrid", UUID.randomUUID().toString()).size();
        }, found -> keyStatus.setText("The key works (" + found + " suggestions for «Madrid»)."),
                failure -> keyStatus.setText("It does not work: " + Dialogs.message(failure)));
    }

    private void saveRules() {
        AppSettings settings;
        try {
            settings = new AppSettings(new DivergenceThresholds(
                    Inputs.integer(distancePercent.getText(), "Distance %", true),
                    Inputs.integer(distanceMinKm.getText(), "Distance km", true),
                    Inputs.integer(durationPercent.getText(), "Duration %", true),
                    Inputs.integer(durationMinMinutes.getText(), "Duration minutes", true),
                    Inputs.integer(consumptionPercent.getText(), "Consumption %", true),
                    Inputs.decimal(tollTolerance.getText(), "Toll tolerance", true)),
                    Inputs.integer(radius.getText(), "Radius", true), brand.getText().trim()
                    .toUpperCase(java.util.Locale.ROOT), defaultCountry.getValue());
        } catch (RuntimeException e) {
            Dialogs.error(e);
            return;
        }
        Background.run(() -> {
            context.settings.save(settings);
            return null;
        }, done -> rulesStatus.setText("Saved: divergences are computed with these rules from now on."));
    }

    private void exportDatabase() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export database");
        chooser.setInitialFileName("control-vehicles-" + LocalDate.now() + ".db");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Control Vehicles database", "*.db"));
        File file = chooser.showSaveDialog(content.getScene().getWindow());
        if (file != null) {
            Background.run(() -> {
                context.transfer.exportTo(file.toPath());
                return file;
            }, written -> Dialogs.info("Database exported", written.getAbsolutePath()
                    + "\n\nHand over this file; the other person imports it from Settings."));
        }
    }

    private void importDatabase() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import database");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Control Vehicles database", "*.db"));
        File file = chooser.showOpenDialog(content.getScene().getWindow());
        if (file == null || !Dialogs.confirm("Replace all the current data?",
                "Everything now in the application is replaced by " + file.getName()
                        + ". A backup of the current data is kept next to it.")) {
            return;
        }
        Background.run(() -> context.transfer.importFrom(file.toPath()), (Path backup) -> {
            Dialogs.info("Database imported", "Previous data saved as " + backup);
            onDatabaseReplaced.run();
        });
    }

    private static Label section(String title) {
        Label label = new Label(title);
        label.getStyleClass().add("section-title");
        return label;
    }

    private static Label hint(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-hint");
        label.setWrapText(true);
        label.setMaxWidth(640);
        return label;
    }
}
