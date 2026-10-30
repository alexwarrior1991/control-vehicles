package com.alejandro.controlvehicles.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

/**
 * One use of a vehicle as the driver declares it. Only the odometer at the start is noted: the distance of a
 * trip is the start of the vehicle's next trip minus this one ({@link OdometerLedger}), never stored.
 * An end time before the start time means the trip ended the next day.
 */
public record Trip(Long id, long vehicleId, long driverId, LocalDate date, LocalTime startTime, LocalTime endTime,
                   Long originPlaceId, Long destinationPlaceId, int startOdometerKm, BigDecimal tollsPaid,
                   String notes, RouteEstimate route, PriceQuote price) {

    public Trip {
        Objects.requireNonNull(date, "date is required");
        Objects.requireNonNull(startTime, "start time is required");
        Objects.requireNonNull(endTime, "end time is required");
        if (startOdometerKm < 0) {
            throw new IllegalArgumentException("odometer cannot be negative");
        }
        tollsPaid = tollsPaid == null ? BigDecimal.ZERO : tollsPaid;
        if (tollsPaid.signum() < 0) {
            throw new IllegalArgumentException("tolls cannot be negative");
        }
        notes = Texts.trimToNull(notes);
    }

    public LocalDateTime startAt() {
        return date.atTime(startTime);
    }

    public LocalDateTime endAt() {
        LocalDateTime end = date.atTime(endTime);
        return endTime.isBefore(startTime) ? end.plusDays(1) : end;
    }

    public java.time.Duration declaredDuration() {
        return java.time.Duration.between(startAt(), endAt());
    }

    public Trip withId(long newId) {
        return new Trip(newId, vehicleId, driverId, date, startTime, endTime, originPlaceId, destinationPlaceId,
                startOdometerKm, tollsPaid, notes, route, price);
    }

    public Trip withRoute(RouteEstimate newRoute) {
        return new Trip(id, vehicleId, driverId, date, startTime, endTime, originPlaceId, destinationPlaceId,
                startOdometerKm, tollsPaid, notes, newRoute, price);
    }

    public Trip withPrice(PriceQuote newPrice) {
        return new Trip(id, vehicleId, driverId, date, startTime, endTime, originPlaceId, destinationPlaceId,
                startOdometerKm, tollsPaid, notes, route, newPrice);
    }
}
