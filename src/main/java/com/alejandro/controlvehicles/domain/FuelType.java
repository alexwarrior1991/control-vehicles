package com.alejandro.controlvehicles.domain;

/** The two fuels the fleet uses. */
public enum FuelType {
    GASOLINE("Gasoline 95"),
    DIESEL("Diesel");

    private final String label;

    FuelType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
