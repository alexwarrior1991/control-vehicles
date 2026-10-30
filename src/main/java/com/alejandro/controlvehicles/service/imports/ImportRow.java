package com.alejandro.controlvehicles.service.imports;

import com.alejandro.controlvehicles.domain.Refuel;

/** What the importer does with one line of the statement ({@code line} counts the header as line 1). */
public record ImportRow(int line, Status status, String plate, Refuel refuel, String message) {

    public enum Status {
        IMPORT("Import"),
        DUPLICATE("Already imported"),
        UNKNOWN_PLATE("Unknown plate"),
        NOT_FUEL("Not fuel"),
        INVALID("Invalid");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
