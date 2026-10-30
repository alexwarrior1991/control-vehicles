package com.alejandro.controlvehicles.service;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reading what is typed or what a statement file carries: decimals accept "1,459" and "1.459", times "8:05",
 * "08:05" and "0805", dates "30/10/2026", "30-10-2026", "2026-10-30" (with a time or not) and Excel's day numbers.
 * Every failure is a {@link ValidationException} naming the field.
 */
public final class Inputs {

    private static final Pattern DATE_TIME = Pattern.compile(
            "(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})(?:[ T]+(\\d{1,2}[:.h]\\d{2})(?::\\d{2})?)?");
    private static final Pattern ISO_DATE_TIME = Pattern.compile(
            "(\\d{4})-(\\d{1,2})-(\\d{1,2})(?:[ T]+(\\d{1,2}:\\d{2})(?::\\d{2}(?:\\.\\d+)?)?)?");
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

    private Inputs() {
    }

    /**
     * A day, with its time if the text carries one ({@code null} time otherwise): day first (Spain and Portugal),
     * ISO, or an Excel day number with its fraction of a day.
     */
    public static LocalDateTime dateTime(String text, String field) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            throw new ValidationException(field + " is required");
        }
        try {
            Matcher dayFirst = DATE_TIME.matcher(value);
            if (dayFirst.matches()) {
                LocalDate day = LocalDate.of(Integer.parseInt(dayFirst.group(3)), Integer.parseInt(dayFirst.group(2)),
                        Integer.parseInt(dayFirst.group(1)));
                return day.atTime(dayFirst.group(4) == null ? LocalTime.MIDNIGHT : time(dayFirst.group(4), field));
            }
            Matcher iso = ISO_DATE_TIME.matcher(value);
            if (iso.matches()) {
                LocalDate day = LocalDate.of(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)),
                        Integer.parseInt(iso.group(3)));
                return day.atTime(iso.group(4) == null ? LocalTime.MIDNIGHT : time(iso.group(4), field));
            }
            if (value.matches("\\d{5}(\\.\\d+)?")) {
                BigDecimal serial = new BigDecimal(value);
                long days = serial.longValue();
                long seconds = serial.subtract(BigDecimal.valueOf(days)).multiply(BigDecimal.valueOf(86_400))
                        .setScale(0, RoundingMode.HALF_UP).longValue();
                return EXCEL_EPOCH.plusDays(days).atStartOfDay().plusSeconds(seconds);
            }
        } catch (java.time.DateTimeException e) {
            throw new ValidationException(field + " is not a valid date: " + value);
        }
        throw new ValidationException(field + " must be a date like 30/10/2026: " + value);
    }

    /** Whether {@link #dateTime} found a time in the text, rather than the start of the day. */
    public static boolean hasTime(String text) {
        String value = text == null ? "" : text.trim();
        Matcher dayFirst = DATE_TIME.matcher(value);
        if (dayFirst.matches()) {
            return dayFirst.group(4) != null;
        }
        Matcher iso = ISO_DATE_TIME.matcher(value);
        if (iso.matches()) {
            return iso.group(4) != null;
        }
        return value.matches("\\d{5}\\.\\d+");
    }

    public static BigDecimal decimal(String text, String field, boolean required) {
        String value = text == null ? "" : text.trim().replace(" ", "").replace("\u00a0", "").replace("€", "")
                .replace("EUR", "").replace("l", "").replace("L", "");
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

    /**
     * A whole number such as an odometer: "120.500", "120,500" and "120 500" are thousands; "120500.4" is rounded.
     */
    public static Integer integer(String text, String field, boolean required) {
        String value = text == null ? "" : text.trim().replace("\u00a0", " ").replaceAll("(?i)\\s*km$", "").trim();
        if (value.isEmpty()) {
            if (required) {
                throw new ValidationException(field + " is required");
            }
            return null;
        }
        if (value.matches("\\d{1,3}([., ]\\d{3})+")) {
            value = value.replaceAll("[., ]", "");
        } else if (value.matches("\\d+[.,]\\d+")) {
            value = new BigDecimal(value.replace(',', '.')).setScale(0, RoundingMode.HALF_UP).toPlainString();
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
