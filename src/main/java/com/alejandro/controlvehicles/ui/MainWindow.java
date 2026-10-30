package com.alejandro.controlvehicles.ui;

import com.alejandro.controlvehicles.app.AppContext;
import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/** The navigation on the left and the chosen screen on the right. */
public final class MainWindow extends BorderPane {

    private final List<AppView> views;
    private final BorderPane center = new BorderPane();
    private AppView current;

    public MainWindow(AppContext context, HostServices hostServices) {
        views = List.of(new TripsView(context, hostServices), new RefuelsView(context), new DivergencesView(context),
                new ReportView(context), new DriversView(context), new VehiclesView(context),
                new FuelPricesView(context), new SettingsView(context, hostServices, this::refreshCurrent));

        Label brand = new Label("Control Vehicles");
        brand.getStyleClass().add("app-title");
        VBox navigation = new VBox(4, brand);
        navigation.getStyleClass().add("navigation");
        navigation.setPadding(new Insets(16, 10, 16, 10));
        navigation.setPrefWidth(190);
        ToggleGroup group = new ToggleGroup();
        for (AppView view : views) {
            ToggleButton button = new ToggleButton(view.title());
            button.setToggleGroup(group);
            button.setMaxWidth(Double.MAX_VALUE);
            button.getStyleClass().add("nav-button");
            button.setOnAction(event -> {
                button.setSelected(true);
                show(view);
            });
            if (view instanceof DriversView) {
                Region gap = new Region();
                gap.setMinHeight(14);
                navigation.getChildren().add(gap);
            }
            navigation.getChildren().add(button);
        }
        Region filler = new Region();
        VBox.setVgrow(filler, Priority.ALWAYS);
        Label database = new Label(context.database.file().getFileName().toString());
        database.getStyleClass().add("field-hint");
        navigation.getChildren().addAll(filler, database);

        center.setPadding(new Insets(16));
        setLeft(navigation);
        setCenter(center);
        ((ToggleButton) navigation.getChildren().get(1)).setSelected(true);
        show(views.getFirst());
    }

    private void show(AppView view) {
        current = view;
        Label title = new Label(view.title());
        title.getStyleClass().add("view-title");
        title.setPadding(new Insets(0, 0, 12, 0));
        center.setTop(title);
        center.setCenter(view.node());
        view.refresh();
    }

    private void refreshCurrent() {
        current.refresh();
    }
}
