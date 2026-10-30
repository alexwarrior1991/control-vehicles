package com.alejandro.controlvehicles.ui;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** ◀ October 2026 ▶ */
public final class MonthSelector extends HBox {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private final ObjectProperty<YearMonth> month = new SimpleObjectProperty<>(YearMonth.now());

    public MonthSelector() {
        super(6);
        setAlignment(Pos.CENTER_LEFT);
        Button previous = new Button("◀");
        Button next = new Button("▶");
        Label label = new Label();
        label.setMinWidth(130);
        label.setAlignment(Pos.CENTER);
        label.getStyleClass().add("month-label");
        label.textProperty().bind(month.map(FORMAT::format));
        previous.setOnAction(event -> month.set(month.get().minusMonths(1)));
        next.setOnAction(event -> month.set(month.get().plusMonths(1)));
        getChildren().addAll(previous, label, next);
    }

    public ObjectProperty<YearMonth> monthProperty() {
        return month;
    }

    public YearMonth month() {
        return month.get();
    }
}
