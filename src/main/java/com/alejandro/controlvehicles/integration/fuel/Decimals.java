package com.alejandro.controlvehicles.integration.fuel;

import java.math.BigDecimal;

/** Prices as the Iberian services write them: "1,589", "1,799 €", "". */
final class Decimals {

    private Decimals() {
    }

    static BigDecimal parse(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.replaceAll("[^0-9,.\\-]", "").replace(',', '.');
        if (cleaned.isEmpty() || cleaned.equals(".")) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(cleaned);
            return value.signum() > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Double coordinate(String text) {
        BigDecimal value = text == null ? null : parseSigned(text);
        return value == null ? null : value.doubleValue();
    }

    private static BigDecimal parseSigned(String text) {
        String cleaned = text.trim().replace(',', '.');
        try {
            return cleaned.isEmpty() ? null : new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
