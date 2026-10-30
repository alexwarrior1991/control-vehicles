package com.alejandro.controlvehicles;

import com.alejandro.controlvehicles.service.ValidationException;
import com.alejandro.controlvehicles.ui.Inputs;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void timesTakeTheUsualShapes() {
        assertEquals(LocalTime.of(8, 5), Inputs.time("8:05", "Start"));
        assertEquals(LocalTime.of(8, 5), Inputs.time("0805", "Start"));
        assertEquals(LocalTime.of(17, 30), Inputs.time("17.30", "End"));
        assertEquals(LocalTime.of(17, 30), Inputs.time("17h30", "End"));
        assertThrows(ValidationException.class, () -> Inputs.time("25:00", "End"));
        assertThrows(ValidationException.class, () -> Inputs.time("", "End"));
    }
}
