package com.alejandro.controlvehicles.integration.mobile;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A trip as the phone app registered it (CONTRACT.md of control-vehicles-mobile): the phone's local times, the GPS
 * positions ({@code null} without a fix) and what the driver typed. {@code endKm} and {@code tolls} are optional.
 */
public record MobileTrip(String id, Long driverId, String driverName, Long vehicleId, String plate,
                         LocalDateTime startAt, Double startLat, Double startLng, Integer startAccuracy,
                         Integer startKm, LocalDateTime endAt, Double endLat, Double endLng, Integer endAccuracy,
                         Integer endKm, BigDecimal tolls, Status status) {

    public enum Status {
        OPEN,
        CLOSED,
        CANCELLED
    }

    public boolean hasStartPosition() {
        return startLat != null && startLng != null;
    }

    public boolean hasEndPosition() {
        return endLat != null && endLng != null;
    }
}
