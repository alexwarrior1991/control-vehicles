package com.alejandro.controlvehicles.ui;

import com.alejandro.controlvehicles.app.AppContext;
import com.alejandro.controlvehicles.domain.Driver;
import com.alejandro.controlvehicles.domain.Formats;
import com.alejandro.controlvehicles.domain.Place;
import com.alejandro.controlvehicles.domain.PriceQuote;
import com.alejandro.controlvehicles.domain.Trip;
import com.alejandro.controlvehicles.domain.Vehicle;
import com.alejandro.controlvehicles.service.SaveResult;
import com.alejandro.controlvehicles.service.ValidationException;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Registering a trip as the driver declares it. Saving asks Google for the route and the price service for the
 * price of the day; if they do not answer, the trip is saved anyway and the warnings say what is missing.
 */
final class TripDialog {

    private TripDialog() {
    }

    static Optional<SaveResult<Trip>> show(Node owner, AppContext context, Trip trip, Place origin,
                                           Place destination) {
        List<Vehicle> vehicles = new ArrayList<>(context.fleet.activeVehicles());
        List<Driver> drivers = new ArrayList<>(context.fleet.activeDrivers());
        if (trip != null) {
            context.fleet.vehicles().stream().filter(v -> v.id() == trip.vehicleId() && !vehicles.contains(v))
                    .forEach(vehicles::add);
            context.fleet.drivers().stream().filter(d -> d.id() == trip.driverId() && !drivers.contains(d))
                    .forEach(drivers::add);
        }
        if (vehicles.isEmpty() || drivers.isEmpty()) {
            Dialogs.info("Nothing to choose yet", "Add at least one vehicle and one driver first.");
            return Optional.empty();
        }

        ComboBox<Vehicle> vehicle = new ComboBox<>();
        vehicle.getItems().setAll(vehicles);
        ComboBox<Driver> driver = new ComboBox<>();
        driver.getItems().setAll(drivers);
        DatePicker date = Dates.picker(trip == null ? LocalDate.now() : trip.date());
        TextField start = new TextField(trip == null ? "" : Formats.time(trip.startTime()));
        start.setPromptText("08:30");
        TextField end = new TextField(trip == null ? "" : Formats.time(trip.endTime()));
        end.setPromptText("10:15");
        PlaceField from = new PlaceField(context.places, "Type the origin…");
        PlaceField to = new PlaceField(context.places, "Type the destination…");
        TextField odometer = new TextField(trip == null ? "" : String.valueOf(trip.startOdometerKm()));
        Label odometerHint = new Label();
        odometerHint.getStyleClass().add("field-hint");
        Button useSuggested = new Button("Use");
        useSuggested.setVisible(false);
        TextField tolls = new TextField(trip == null ? "0" : VehiclesView.plain(trip.tollsPaid()));
        TextField manualPrice = new TextField(trip != null && trip.price() != null && trip.price().isManual()
                ? trip.price().pricePerLitre().toPlainString() : "");
        manualPrice.setPromptText("Automatic (Galp stations around the origin)");
        TextArea notes = new TextArea(trip == null ? "" : DriversView.nullToEmpty(trip.notes()));
        notes.setPrefRowCount(2);

        if (trip != null) {
            vehicles.stream().filter(v -> v.id() == trip.vehicleId()).findFirst().ifPresent(vehicle::setValue);
            drivers.stream().filter(d -> d.id() == trip.driverId()).findFirst().ifPresent(driver::setValue);
            from.select(origin);
            to.select(destination);
        } else {
            vehicle.setValue(vehicles.getFirst());
            if (drivers.size() == 1) {
                driver.setValue(drivers.getFirst());
            }
        }

        int[] suggested = {-1};
        Runnable suggest = () -> {
            Vehicle chosen = vehicle.getValue();
            LocalDate day = date.getValue();
            if (chosen == null || day == null) {
                return;
            }
            LocalTime at;
            try {
                at = Inputs.time(start.getText(), "Start");
            } catch (ValidationException e) {
                at = LocalTime.MAX;
            }
            LocalTime moment = at;
            Background.run(() -> context.trips.suggestedStartOdometer(chosen.id(), day.atTime(moment)), km -> {
                suggested[0] = km;
                odometerHint.setText("Last known reading: " + Formats.km(km) + " km");
                useSuggested.setVisible(true);
                if (trip == null && odometer.getText().isBlank()) {
                    odometer.setText(String.valueOf(km));
                }
            });
        };
        useSuggested.setOnAction(event -> {
            if (suggested[0] >= 0) {
                odometer.setText(String.valueOf(suggested[0]));
            }
        });
        vehicle.valueProperty().addListener((o, b, n) -> suggest.run());
        date.valueProperty().addListener((o, b, n) -> suggest.run());
        start.focusedProperty().addListener((o, b, focused) -> {
            if (!focused) {
                suggest.run();
            }
        });
        suggest.run();

        HBox times = new HBox(8, start, new Label("to"), end);
        HBox.setHgrow(start, Priority.ALWAYS);
        HBox.setHgrow(end, Priority.ALWAYS);
        HBox odometerRow = new HBox(8, odometer, useSuggested);
        HBox.setHgrow(odometer, Priority.ALWAYS);
        VBox odometerBox = new VBox(2, odometerRow, odometerHint);
        Label explanation = new Label("Only the odometer at the start is noted: the trip's distance is known when the"
                + " vehicle's next trip is registered.");
        explanation.setWrapText(true);
        explanation.getStyleClass().add("field-hint");
        explanation.setMaxWidth(520);

        FormGrid form = new FormGrid().add("Vehicle *", vehicle).add("Driver *", driver).add("Date *", date)
                .add("Start – end *", times).add("Origin *", from).add("Destination *", to)
                .add("Start odometer (km) *", odometerBox).add("Tolls paid (€)", tolls)
                .add("Fuel price (€/l)", manualPrice).add("Notes", notes).addWide(explanation);

        return EditorDialog.show(owner, trip == null ? "New trip" : "Edit trip", form, () -> {
            if (vehicle.getValue() == null) {
                throw new ValidationException("Choose a vehicle");
            }
            if (driver.getValue() == null) {
                throw new ValidationException("Choose a driver");
            }
            if (date.getValue() == null) {
                throw new ValidationException("Date is required");
            }
            if (from.selected() == null || to.selected() == null) {
                throw new ValidationException("Choose the origin and the destination from the suggestions");
            }
            BigDecimal price = Inputs.decimal(manualPrice.getText(), "Fuel price", false);
            Trip edited = new Trip(trip == null ? null : trip.id(), vehicle.getValue().id(), driver.getValue().id(),
                    date.getValue(), Inputs.time(start.getText(), "Start"), Inputs.time(end.getText(), "End"),
                    from.selected().id(), to.selected().id(),
                    Inputs.integer(odometer.getText(), "Start odometer", true),
                    Inputs.decimal(tolls.getText(), "Tolls", false), notes.getText(),
                    trip == null ? null : trip.route(), trip == null ? null : trip.price());
            PriceQuote manual = price == null ? null : PriceQuote.manual(price);
            Callable<SaveResult<Trip>> save = () -> context.trips.save(edited, manual);
            return save;
        });
    }
}
