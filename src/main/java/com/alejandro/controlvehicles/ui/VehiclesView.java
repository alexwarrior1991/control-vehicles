package com.alejandro.controlvehicles.ui;

import com.alejandro.controlvehicles.app.AppContext;
import com.alejandro.controlvehicles.domain.Formats;
import com.alejandro.controlvehicles.domain.FuelType;
import com.alejandro.controlvehicles.domain.Vehicle;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import java.math.BigDecimal;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static com.alejandro.controlvehicles.ui.DriversView.nullToEmpty;

/** The vehicles, with the nominal consumption every expected cost uses. */
public final class VehiclesView implements AppView {

    private final AppContext context;
    private final BorderPane root = new BorderPane();
    private final TableView<Vehicle> table = Tables.table("No vehicles yet: add the first one with «New vehicle».");

    public VehiclesView(AppContext context) {
        this.context = context;
        table.getColumns().add(Tables.text("Plate", Vehicle::plate));
        table.getColumns().add(Tables.text("Brand", v -> nullToEmpty(v.brand())));
        table.getColumns().add(Tables.text("Model", v -> nullToEmpty(v.model())));
        table.getColumns().add(Tables.text("Fuel", v -> v.fuelType().label()));
        table.getColumns().add(Tables.number("Nominal l/100 km", Vehicle::nominalConsumptionL100, Formats::decimal));
        table.getColumns().add(Tables.number("Tank (l)", Vehicle::tankCapacityL, Formats::decimal));
        table.getColumns().add(Tables.number("Initial km", Vehicle::initialOdometerKm, Formats::km));
        table.getColumns().add(Tables.text("Active", v -> v.active() ? "Yes" : "No"));
        Tables.onDoubleClick(table, this::edit);

        Button add = new Button("New vehicle");
        add.getStyleClass().add("primary");
        add.setOnAction(event -> edit(null));
        Button edit = new Button("Edit");
        edit.setOnAction(event -> selected(this::edit));
        Button toggle = new Button("Activate / deactivate");
        toggle.setOnAction(event -> selected(vehicle -> Background.run(
                () -> context.fleet.setActive(vehicle, !vehicle.active()), saved -> refresh())));
        Button delete = new Button("Delete");
        delete.setOnAction(event -> selected(this::delete));
        HBox actions = new HBox(8, add, edit, toggle, delete);
        actions.setPadding(new Insets(0, 0, 10, 0));
        root.setTop(actions);
        root.setCenter(table);
    }

    @Override
    public String title() {
        return "Vehicles";
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        Background.run(context.fleet::vehicles, table.getItems()::setAll);
    }

    private void edit(Vehicle vehicle) {
        TextField plate = new TextField(vehicle == null ? "" : vehicle.plate());
        TextField brand = new TextField(vehicle == null ? "" : nullToEmpty(vehicle.brand()));
        TextField model = new TextField(vehicle == null ? "" : nullToEmpty(vehicle.model()));
        ComboBox<FuelType> fuel = new ComboBox<>();
        fuel.getItems().setAll(FuelType.values());
        fuel.setValue(vehicle == null ? FuelType.DIESEL : vehicle.fuelType());
        TextField consumption = new TextField(vehicle == null ? "" : plain(vehicle.nominalConsumptionL100()));
        consumption.setPromptText("e.g. 6,5");
        TextField tank = new TextField(vehicle == null ? "" : plain(vehicle.tankCapacityL()));
        TextField initial = new TextField(vehicle == null ? "0" : String.valueOf(vehicle.initialOdometerKm()));
        CheckBox active = new CheckBox("Active");
        active.setSelected(vehicle == null || vehicle.active());
        TextArea notes = new TextArea(vehicle == null ? "" : nullToEmpty(vehicle.notes()));
        notes.setPrefRowCount(3);
        FormGrid form = new FormGrid().add("Plate *", plate).add("Brand", brand).add("Model", model)
                .add("Fuel *", fuel).add("Nominal l/100 km *", consumption).add("Tank capacity (l)", tank)
                .add("Initial odometer (km)", initial).add("", active).add("Notes", notes);
        EditorDialog.show(root, vehicle == null ? "New vehicle" : "Edit vehicle", form, () -> {
            Vehicle edited = new Vehicle(vehicle == null ? null : vehicle.id(), plate.getText(), brand.getText(),
                    model.getText(), fuel.getValue(),
                    Inputs.decimal(consumption.getText(), "Nominal consumption", true),
                    Inputs.decimal(tank.getText(), "Tank capacity", false),
                    Inputs.integer(initial.getText(), "Initial odometer", true), active.isSelected(),
                    notes.getText());
            Callable<Vehicle> save = () -> context.fleet.save(edited);
            return save;
        }).ifPresent(saved -> refresh());
    }

    private void delete(Vehicle vehicle) {
        if (Dialogs.confirm("Delete " + vehicle.plate() + "?",
                "A vehicle with trips or refuels cannot be deleted: deactivate it instead.")) {
            Background.run(() -> {
                context.fleet.deleteVehicle(vehicle.id());
                return null;
            }, done -> refresh());
        }
    }

    private void selected(Consumer<Vehicle> action) {
        Vehicle vehicle = table.getSelectionModel().getSelectedItem();
        if (vehicle != null) {
            action.accept(vehicle);
        }
    }

    static String plain(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }
}
