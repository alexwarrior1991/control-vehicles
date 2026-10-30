package com.alejandro.controlvehicles.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** How numbers, money, dates and durations are written everywhere in the application. */
public final class Formats {

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    public static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private Formats() {
    }

    public static String money(BigDecimal amount) {
        return amount == null ? "" : pattern("#,##0.00").format(amount.setScale(2, RoundingMode.HALF_UP));
    }

    public static String decimal(BigDecimal value) {
        return value == null ? "" : pattern("#,##0.##").format(value);
    }

    public static String pricePerLitre(BigDecimal value) {
        return value == null ? "" : pattern("0.000").format(value);
    }

    public static String km(Integer km) {
        return km == null ? "" : pattern("#,##0").format(km);
    }

    public static String km(BigDecimal km) {
        return km == null ? "" : pattern("#,##0.0").format(km);
    }

    public static String date(LocalDate date) {
        return date == null ? "" : DATE.format(date);
    }

    public static String time(LocalTime time) {
        return time == null ? "" : TIME.format(time);
    }

    public static String dateTime(LocalDateTime dateTime) {
        return dateTime == null ? "" : DATE_TIME.format(dateTime);
    }

    /** "1:05", "0:45": for narrow columns. */
    public static String shortDuration(Duration duration) {
        if (duration == null) {
            return "";
        }
        long minutes = duration.toMinutes();
        return String.format(Locale.ENGLISH, "%s%d:%02d", minutes < 0 ? "-" : "", Math.abs(minutes) / 60,
                Math.abs(minutes) % 60);
    }

    /** "1 h 05 min", "45 min". */
    public static String duration(Duration duration) {
        if (duration == null) {
            return "";
        }
        long minutes = duration.toMinutes();
        String sign = minutes < 0 ? "-" : "";
        minutes = Math.abs(minutes);
        return minutes >= 60
                ? String.format(Locale.ENGLISH, "%s%d h %02d min", sign, minutes / 60, minutes % 60)
                : String.format(Locale.ENGLISH, "%s%d min", sign, minutes);
    }

    private static DecimalFormat pattern(String pattern) {
        return new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.ENGLISH));
    }
}
