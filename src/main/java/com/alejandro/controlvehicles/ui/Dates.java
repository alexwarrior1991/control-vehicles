package com.alejandro.controlvehicles.ui;

import com.alejandro.controlvehicles.domain.Formats;
import javafx.scene.control.DatePicker;
import javafx.util.converter.LocalDateStringConverter;

import java.time.LocalDate;

/** Date pickers that read and write dd/MM/yyyy whatever the computer's locale. */
public final class Dates {

    private Dates() {
    }

    public static DatePicker picker(LocalDate value) {
        DatePicker picker = new DatePicker(value);
        picker.setConverter(new LocalDateStringConverter(Formats.DATE, Formats.DATE));
        picker.setPromptText("dd/mm/yyyy");
        return picker;
    }
}
