package com.alejandro.controlvehicles.app;

import javafx.application.Application;

/**
 * The entry point of the jar. It does not extend {@link Application} on purpose: that lets the single jar start
 * with JavaFX on the class path.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(ControlVehiclesApp.class, args);
    }
}
