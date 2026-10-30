package com.alejandro.controlvehicles.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

/** A two-column form: label, field. */
public final class FormGrid extends GridPane {

    private int row;

    public FormGrid() {
        setHgap(10);
        setVgap(8);
        setPadding(new Insets(12));
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(140);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        fields.setMinWidth(360);
        getColumnConstraints().addAll(labels, fields);
    }

    public FormGrid add(String label, Node field) {
        add(new Label(label), 0, row);
        add(field, 1, row);
        row++;
        return this;
    }

    public FormGrid addWide(Node node) {
        add(node, 0, row, 2, 1);
        row++;
        return this;
    }
}
