package com.alejandro.controlvehicles.ui;

import com.alejandro.controlvehicles.app.AppContext;
import com.alejandro.controlvehicles.domain.Country;
import com.alejandro.controlvehicles.domain.Formats;
import com.alejandro.controlvehicles.domain.FuelPrice;
import com.alejandro.controlvehicles.domain.FuelType;
import com.alejandro.controlvehicles.service.Inputs;
import com.alejandro.controlvehicles.service.ValidationException;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * The stored country-wide prices: the history used when a trip's day has no station prices. Spain's come from
 * MITECO (also for past days), Portugal's from DGEG (today only); any day can be typed by hand.
 */
public final class FuelPricesView implements AppView {

    private final AppContext context;
    private final BorderPane root = new BorderPane();
    private final TableView<FuelPrice> table = Tables.table("No prices stored yet.");
    private final Label status = new Label();

    public FuelPricesView(AppContext context) {
        this.context = context;
        table.getColumns().add(Tables.number("Date", FuelPrice::date, Formats::date));
        table.getColumns().add(Tables.text("Country", p -> p.country().label()));
        table.getColumns().add(Tables.text("Fuel", p -> p.fuelType().label()));
        table.getColumns().add(Tables.text("Brand", p -> p.brand() == null ? "All brands" : p.brand()));
        table.getColumns().add(Tables.number("Average €/l", FuelPrice::averagePrice, Formats::pricePerLitre));
        table.getColumns().add(Tables.number("Min €/l", FuelPrice::minPrice, Formats::pricePerLitre));
        table.getColumns().add(Tables.number("Max €/l", FuelPrice::maxPrice, Formats::pricePerLitre));
        table.getColumns().add(Tables.number("Stations", FuelPrice::stations, String::valueOf));
        table.getColumns().add(Tables.text("Source", p -> p.source().name()));

        Button spain = new Button("Fetch today's prices · Spain");
        spain.setOnAction(event -> fetch(Country.ES));
        Button portugal = new Button("Fetch today's prices · Portugal");
        portugal.setOnAction(event -> fetch(Country.PT));
        Button manual = new Button("Add a price by hand");
        manual.setOnAction(event -> manual());
        Button delete = new Button("Delete");
        delete.setOnAction(event -> {
            FuelPrice price = table.getSelectionModel().getSelectedItem();
            if (price != null && Dialogs.confirm("Delete this price?", "")) {
                Background.run(() -> {
                    context.fuelPrices.delete(price.id());
                    return null;
                }, done -> refresh());
            }
        });
        Label explanation = new Label("A trip's price is the average of the Galp stations within the configured"
                + " radius of its origin on its day; these stored averages are the fallback.");
        explanation.getStyleClass().add("field-hint");
        explanation.setWrapText(true);
        VBox top = new VBox(8, new HBox(8, spain, portugal, manual, delete), explanation, status);
        top.setPadding(new Insets(0, 0, 10, 0));
        root.setTop(top);
        root.setCenter(table);
    }

    @Override
    public String title() {
        return "Fuel prices";
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        Background.run(context.fuelPrices::all, table.getItems()::setAll);
    }

    private void fetch(Country country) {
        status.setText("Asking the " + country.label() + " price service… (it can take a minute)");
        Background.run(() -> context.fuelPrices.fetchToday(country), (List<FuelPrice> stored) -> {
            status.setText(stored.size() + " prices stored for " + country.label() + " today.");
            refresh();
        }, failure -> {
            status.setText("");
            Dialogs.error(failure);
        });
    }

    private void manual() {
        DatePicker date = Dates.picker(LocalDate.now());
        ComboBox<Country> country = new ComboBox<>();
        country.getItems().setAll(Country.values());
        country.setValue(context.settings.load().defaultCountry());
        ComboBox<FuelType> fuel = new ComboBox<>();
        fuel.getItems().setAll(FuelType.values());
        fuel.setValue(FuelType.DIESEL);
        TextField brand = new TextField("GALP");
        brand.setPromptText("Empty = all brands");
        TextField price = new TextField();
        price.setPromptText("1,459");
        FormGrid form = new FormGrid().add("Date *", date).add("Country *", country).add("Fuel *", fuel)
                .add("Brand", brand).add("Price (€/l) *", price);
        EditorDialog.show(root, "Fuel price", form, () -> {
            if (date.getValue() == null) {
                throw new ValidationException("Date is required");
            }
            FuelPrice entered = FuelPrice.manual(date.getValue(), country.getValue(), fuel.getValue(),
                    brand.getText().isBlank() ? null : brand.getText().trim().toUpperCase(java.util.Locale.ROOT),
                    Inputs.decimal(price.getText(), "Price", true));
            Callable<FuelPrice> save = () -> context.fuelPrices.saveManual(entered);
            return save;
        }).ifPresent(saved -> refresh());
    }
}
