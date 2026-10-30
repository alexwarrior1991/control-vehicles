package com.alejandro.controlvehicles.ui;

import javafx.scene.Node;

/** A screen of the main window; {@link #refresh()} runs every time it is shown. */
public interface AppView {

    String title();

    Node node();

    void refresh();
}
