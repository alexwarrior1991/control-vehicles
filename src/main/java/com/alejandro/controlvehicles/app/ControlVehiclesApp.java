package com.alejandro.controlvehicles.app;

import com.alejandro.controlvehicles.ui.MainWindow;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.time.Clock;
import java.util.Objects;

public final class ControlVehiclesApp extends Application {

    @Override
    public void start(Stage stage) {
        AppContext context = new AppContext(AppContext.defaultHome(), Clock.systemDefaultZone());
        MainWindow window = new MainWindow(context, getHostServices());
        Scene scene = new Scene(window, 1280, 800);
        scene.getStylesheets().add(Objects.requireNonNull(
                ControlVehiclesApp.class.getResource("/com/alejandro/controlvehicles/ui/app.css")).toExternalForm());
        stage.setTitle("Control Vehicles");
        stage.setScene(scene);
        stage.setMinWidth(960);
        stage.setMinHeight(600);
        stage.show();
    }
}
