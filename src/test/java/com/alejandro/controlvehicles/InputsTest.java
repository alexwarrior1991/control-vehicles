package com.alejandro.controlvehicles;

import com.alejandro.controlvehicles.service.ValidationException;
import com.alejandro.controlvehicles.service.Inputs;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What people type in Spain and Portugal is read as they mean it. */
class InputsTest {

    @Test
    void decimalsTakeACommaOrADot() {
        assertEquals(new BigDecimal("1.459"), Inputs.decimal("1,459", "Price", true));
        assertEquals(new BigDecimal("1.459"), Inputs.decimal(" 1.459 ", "Price", true));
        assertEquals(new BigDecimal("1234.56"), Inputs.decimal("1.234,56", "Amount", true));
        assertEquals(new BigDecimal("1234.56"), Inputs.decimal("1,234.56", "Amount", true));
        assertNull(Inputs.decimal("", "Tank", false));
        assertThrows(ValidationException.class, () -> Inputs.decimal("", "Price", true));
        assertThrows(ValidationException.class, () -> Inputs.decimal("abc", "Price", true));
    }

    @Test
    void odometersTakeThousandSeparators() {
        assertEquals(120500, Inputs.integer("120.500", "Odometer", true));
        assertEquals(120500, Inputs.integer("120 500", "Odometer", true));
        assertThrows(ValidationException.class, () -> Inputs.integer("12a", "Odometer", true));
    }

    @Test
    void datesTakeDayFirstIsoAndExcelDayNumbers() {
        assertEquals(java.time.LocalDateTime.of(2026, 10, 5, 0, 0), Inputs.dateTime("05/10/2026", "Date"));
        assertEquals(java.time.LocalDateTime.of(2026, 10, 5, 8, 15), Inputs.dateTime("5-10-2026 08:15", "Date"));
        assertEquals(java.time.LocalDateTime.of(2026, 10, 5, 8, 15), Inputs.dateTime("2026-10-05T08:15:30", "Date"));
        assertEquals(java.time.LocalDateTime.of(2026, 10, 5, 12, 0), Inputs.dateTime("46300.5", "Date"));
        assertTrue(Inputs.hasTime("05/10/2026 8:15"));
        assertFalse(Inputs.hasTime("05/10/2026"));
        assertThrows(ValidationException.class, () -> Inputs.dateTime("31/02/2026", "Date"));
        assertThrows(ValidationException.class, () -> Inputs.dateTime("yesterday", "Date"));
    }

    @Test
    void wholeNumbersTellThousandsFromDecimals() {
        assertEquals(120400, Inputs.integer("120.400", "Odometer", true));
        assertEquals(120400, Inputs.integer("120400 km", "Odometer", true));
        assertEquals(120400, Inputs.integer("120399.6", "Odometer", true));
    }

    @Test
    void timesTakeTheUsualShapes() {
        assertEquals(LocalTime.of(8, 5), Inputs.time("8:05", "Start"));
        assertEquals(LocalTime.of(8, 5), Inputs.time("0805", "Start"));
        assertEquals(LocalTime.of(17, 30), Inputs.time("17.30", "End"));
        assertEquals(LocalTime.of(17, 30), Inputs.time("17h30", "End"));
        assertThrows(ValidationException.class, () -> Inputs.time("25:00", "End"));
        assertThrows(ValidationException.class, () -> Inputs.time("", "End"));
    }
}
