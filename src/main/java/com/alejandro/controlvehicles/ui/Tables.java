package com.alejandro.controlvehicles.ui;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;

import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Function;

/** Column and table builders, so every list looks and sorts the same. */
public final class Tables {

    private Tables() {
    }

    public static <T> TableView<T> table(String emptyText) {
        TableView<T> table = new TableView<>();
        table.setPlaceholder(new Label(emptyText));
        // El «+» de la cabecera deja esconder y volver a enseñar columnas.
        table.setTableMenuButtonVisible(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        return table;
    }

    /**
     * For lists with many columns: each column keeps the width its header needs and the table scrolls
     * sideways instead of cutting every cell.
     */
    public static <T> TableView<T> wide(TableView<T> table) {
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        return table;
    }

    /** A header over several columns, so that each one can carry a short title ("Km" over "Odometer", "Google"). */
    @SafeVarargs
    public static <T> TableColumn<T, ?> group(String title, TableColumn<T, ?>... columns) {
        TableColumn<T, Object> group = new TableColumn<>(title);
        group.getColumns().addAll(columns);
        return group;
    }

    public static <T, C extends TableColumn<T, ?>> C width(C column, double width) {
        column.setPrefWidth(width);
        return column;
    }

    public static <T> TableColumn<T, String> text(String title, Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setPrefWidth(headerWidth(title));
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    /** A column shown with {@code format} but sorted by the raw value; right-aligned, for numbers. */
    public static <T, V extends Comparable<? super V>> TableColumn<T, V> number(String title, Function<T, V> value,
                                                                     Function<V, String> format) {
        TableColumn<T, V> column = new TableColumn<>(title);
        column.setPrefWidth(headerWidth(title));
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        column.setComparator(Comparator.nullsFirst(Comparator.naturalOrder()));
        column.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(V item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : format.apply(item));
                setAlignment(Pos.CENTER_RIGHT);
            }
        });
        return column;
    }

    private static double headerWidth(String title) {
        return Math.max(90, title.length() * 9 + 30);
    }

    public static <T> void onDoubleClick(TableView<T> table, Consumer<T> action) {
        table.setRowFactory(t -> {
            TableRow<T> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    action.accept(row.getItem());
                }
            });
            return row;
        });
    }
}
