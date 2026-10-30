package com.alejandro.controlvehicles.domain;

/** Every way what is declared can disagree with what really happened. */
public enum DivergenceKind {
    DISTANCE_MISMATCH("Odometer vs Google distance", "distance"),
    DURATION_MISMATCH("Declared vs Google duration", "time"),
    ODOMETER_NOT_INCREASING("Odometer not increasing", "odometer"),
    UNRECORDED_USE("Unrecorded use between trips", "unrecorded"),
    VEHICLE_OVERLAP("Vehicle in two trips at once", "overlap"),
    DRIVER_OVERLAP("Driver in two trips at once", "overlap"),
    TOLL_MISMATCH("Declared vs Google tolls", "tolls"),
    REFUEL_ODOMETER_OUT_OF_SEQUENCE("Refuel odometer out of sequence", "refuel km"),
    REFUEL_OVER_CAPACITY("Refuel above tank capacity", "capacity"),
    CONSUMPTION_MISMATCH("Measured vs nominal consumption", "consumption");

    private final String label;
    private final String shortLabel;

    DivergenceKind(String label, String shortLabel) {
        this.label = label;
        this.shortLabel = shortLabel;
    }

    public String label() {
        return label;
    }

    /** One word, for a narrow column. */
    public String shortLabel() {
        return shortLabel;
    }

    @Override
    public String toString() {
        return label;
    }
}
