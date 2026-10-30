package com.alejandro.controlvehicles.ui;

import javafx.scene.control.ComboBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** An entry of a filter combo, where {@code value == null} is "all". */
public record Choice<T>(String label, T value) {

    @Override
    public String toString() {
        return label;
    }

    /** Fills a filter combo with "all" plus the items, keeping the current selection when it is still there. */
    public static <T> void fill(ComboBox<Choice<T>> combo, String allLabel, List<T> items, Function<T, String> label,
                                Function<T, Object> key) {
        Choice<T> current = combo.getValue();
        List<Choice<T>> choices = new ArrayList<>();
        choices.add(new Choice<>(allLabel, null));
        items.forEach(item -> choices.add(new Choice<>(label.apply(item), item)));
        combo.getItems().setAll(choices);
        Choice<T> keep = choices.getFirst();
        if (current != null && current.value() != null) {
            Object wanted = key.apply(current.value());
            keep = choices.stream().filter(c -> c.value() != null && Objects.equals(key.apply(c.value()), wanted))
                    .findFirst().orElse(keep);
        }
        combo.setValue(keep);
    }
}
