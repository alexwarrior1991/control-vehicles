package com.alejandro.controlvehicles.domain;

/** Every way what is declared can disagree with what really happened. */
public enum DivergenceKind {
    DISTANCE_MISMATCH("Odometer vs Google distance"),
    DURATION_MISMATCH("Declared vs Google duration"),
    ODOMETER_NOT_INCREASING("Odometer not increasing"),
    UNRECORDED_USE("Unrecorded use between trips"),
    VEHICLE_OVERLAP("Vehicle in two trips at once"),
    DRIVER_OVERLAP("Driver in two trips at once"),
    TOLL_MISMATCH("Declared vs Google tolls"),
    REFUEL_ODOMETER_OUT_OF_SEQUENCE("Refuel odometer out of sequence"),
    REFUEL_OVER_CAPACITY("Refuel above tank capacity"),
    CONSUMPTION_MISMATCH("Measured vs nominal consumption");

    private final String label;

    DivergenceKind(String label) {
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
