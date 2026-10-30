package com.alejandro.controlvehicles.support;

import com.alejandro.controlvehicles.domain.FuelType;
import com.alejandro.controlvehicles.domain.PriceQuote;
import com.alejandro.controlvehicles.domain.Refuel;
import com.alejandro.controlvehicles.domain.RouteEstimate;
import com.alejandro.controlvehicles.domain.TollStatus;
import com.alejandro.controlvehicles.domain.Trip;
import com.alejandro.controlvehicles.domain.Vehicle;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Short builders for the domain records the tests need. */
public final class Fixtures {

    private Fixtures() {
    }

    public static Vehicle vehicle(long id, String nominal) {
        return new Vehicle(id, "1234 ABC" + id, "Renault", "Kangoo", FuelType.DIESEL, new BigDecimal(nominal),
                new BigDecimal("60"), 0, true, null);
    }

    public static Trip trip(long id, long vehicleId, long driverId, String date, String start, String end,
                            int odometer) {
        return new Trip(id, vehicleId, driverId, LocalDate.parse(date), LocalTime.parse(start), LocalTime.parse(end),
                1L, 2L, odometer, null, BigDecimal.ZERO, null, null, null);
    }

    public static Trip withEnd(Trip trip, int endOdometer) {
        return new Trip(trip.id(), trip.vehicleId(), trip.driverId(), trip.date(), trip.startTime(), trip.endTime(),
                trip.originPlaceId(), trip.destinationPlaceId(), trip.startOdometerKm(), endOdometer, trip.tollsPaid(),
                trip.notes(), trip.route(), trip.price());
    }

    public static Trip withGoogle(Trip trip, int meters, int seconds) {
        return trip.withRoute(new RouteEstimate(meters, seconds, TollStatus.NONE, null, Instant.EPOCH));
    }

    public static Trip withPrice(Trip trip, String price) {
        return trip.withPrice(new PriceQuote(new BigDecimal(price), "test"));
    }

    public static Refuel estimated(Refuel refuel) {
        return new Refuel(refuel.id(), refuel.vehicleId(), refuel.driverId(), refuel.refuelAt(), refuel.odometerKm(),
                true, refuel.litres(), refuel.totalAmount(), refuel.fullTank(), refuel.station(), refuel.brand(),
                refuel.country(), refuel.notes());
    }

    public static Refuel refuel(long id, long vehicleId, String at, int odometer, String litres, String amount,
                                boolean full) {
        return new Refuel(id, vehicleId, null, LocalDateTime.parse(at), odometer, false, new BigDecimal(litres),
                new BigDecimal(amount), full, null, "Galp", null, null);
    }
}
