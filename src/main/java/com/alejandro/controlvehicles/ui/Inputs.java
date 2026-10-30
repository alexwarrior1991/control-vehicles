package com.alejandro.controlvehicles.ui;

import com.alejandro.controlvehicles.service.ValidationException;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/**
 * Reading what is typed: decimals accept "1,459" and "1.459", times "8:05", "08:05" and "0805". Every failure is
 * a {@link ValidationException} naming the field.
 */
public final class Inputs {

    private Inputs() {
    }

    public static BigDecimal decimal(String text, String field, boolean required) {
        String value = text == null ? "" : text.trim().replace(" ", "");
        if (value.isEmpty()) {
            if (required) {
                throw new ValidationException(field + " is required");
            }
            return null;
        }
        if (value.contains(",") && value.contains(".")) {
            // "1.234,56" o "1,234.56": el último separador es el decimal.
            boolean commaIsDecimal = value.lastIndexOf(',') > value.lastIndexOf('.');
            value = commaIsDecimal ? value.replace(".", "").replace(',', '.') : value.replace(",", "");
        } else {
            value = value.replace(',', '.');
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new ValidationException(field + " is not a number: " + text.trim());
        }
    }

    public static Integer integer(String text, String field, boolean required) {
        String value = text == null ? "" : text.trim().replace(".", "").replace(",", "").replace(" ", "");
        if (value.isEmpty()) {
            if (required) {
                throw new ValidationException(field + " is required");
            }
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ValidationException(field + " is not a whole number: " + text.trim());
        }
    }

    public static LocalTime time(String text, String field) {
        String value = text == null ? "" : text.trim().replace('.', ':').replace('h', ':');
        if (value.isEmpty()) {
            throw new ValidationException(field + " is required");
        }
        if (value.matches("\\d{3,4}")) {
            value = value.substring(0, value.length() - 2) + ":" + value.substring(value.length() - 2);
        }
        if (value.matches("\\d:\\d{2}")) {
            value = "0" + value;
        }
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new ValidationException(field + " must be a time like 08:30");
        }
    }
}
