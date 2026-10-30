package com.alejandro.controlvehicles.app;

import com.alejandro.controlvehicles.ui.AppLook;
import com.alejandro.controlvehicles.ui.MainWindow;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.time.Clock;

public final class ControlVehiclesApp extends Application {

    @Override
    public void start(Stage stage) {
        AppLook.load();
        AppContext context = new AppContext(AppContext.defaultHome(), Clock.systemDefaultZone());
        MainWindow window = new MainWindow(context, getHostServices());
        Scene scene = new Scene(window, 1280, 800);
        AppLook.style(scene);
        stage.setTitle("Control Vehicles");
        stage.setScene(scene);
        stage.setMinWidth(960);
        stage.setMinHeight(600);
        stage.show();
    }
}
