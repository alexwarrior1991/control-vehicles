package com.alejandro.controlvehicles.persistence;

import com.alejandro.controlvehicles.domain.PriceQuote;
import com.alejandro.controlvehicles.domain.RouteEstimate;
import com.alejandro.controlvehicles.domain.TollStatus;
import com.alejandro.controlvehicles.domain.Trip;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class TripRepository {

    private final Database database;

    public TripRepository(Database database) {
        this.database = database;
    }

    public List<Trip> findAll() {
        return database.read(c -> Sql.list(c, "SELECT * FROM trip ORDER BY trip_date, start_time, id",
                TripRepository::map));
    }

    public List<Trip> findByVehicle(long vehicleId) {
        return database.read(c -> Sql.list(c,
                "SELECT * FROM trip WHERE vehicle_id = ? ORDER BY trip_date, start_time, id",
                TripRepository::map, vehicleId));
    }

    public Optional<Trip> findById(long id) {
        return database.read(c -> Sql.one(c, "SELECT * FROM trip WHERE id = ?", TripRepository::map, id));
    }

    public Trip save(Trip trip) {
        RouteEstimate route = trip.route();
        PriceQuote price = trip.price();
        Object[] values = {trip.vehicleId(), trip.driverId(), trip.date(), trip.startTime(), trip.endTime(),
                trip.originPlaceId(), trip.destinationPlaceId(), trip.startOdometerKm(), trip.endOdometerKm(),
                trip.tollsPaid(), trip.notes(),
                route == null ? null : route.distanceMeters(), route == null ? null : route.durationSeconds(),
                route == null ? null : route.tollStatus(), route == null ? null : route.tollEstimate(),
                route == null ? null : route.computedAt(),
                price == null ? null : price.pricePerLitre(), price == null ? null : price.source()};
        return database.transaction(c -> {
            String now = Rows.now();
            if (trip.id() == null) {
                long id = Sql.insert(c, """
                        INSERT INTO trip (vehicle_id, driver_id, trip_date, start_time, end_time, origin_place_id,
                                          destination_place_id, start_odometer_km, end_odometer_km,
                                          tolls_paid, notes,
                                          google_distance_m, google_duration_s, google_toll_status,
                                          google_toll_estimate, google_computed_at, fuel_price_per_l,
                                          fuel_price_source, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", append(values, now, now));
                return trip.withId(id);
            }
            Sql.update(c, """
                    UPDATE trip SET vehicle_id = ?, driver_id = ?, trip_date = ?, start_time = ?, end_time = ?,
                                    origin_place_id = ?, destination_place_id = ?, start_odometer_km = ?,
                                    end_odometer_km = ?, tolls_paid = ?, notes = ?, google_distance_m = ?,
                                    google_duration_s = ?, google_toll_status = ?, google_toll_estimate = ?, google_computed_at = ?,
                                    fuel_price_per_l = ?, fuel_price_source = ?, updated_at = ?
                    WHERE id = ?""", append(values, now, trip.id()));
            return trip;
        });
    }

    public void delete(long id) {
        database.transaction(c -> Sql.update(c, "DELETE FROM trip WHERE id = ?", id));
    }

    private static Object[] append(Object[] values, Object... more) {
        Object[] all = new Object[values.length + more.length];
        System.arraycopy(values, 0, all, 0, values.length);
        System.arraycopy(more, 0, all, values.length, more.length);
        return all;
    }

    private static Trip map(ResultSet rs) throws SQLException {
        Integer distance = Rows.intOrNull(rs, "google_distance_m");
        RouteEstimate route = distance == null ? null : new RouteEstimate(distance, rs.getInt("google_duration_s"),
                TollStatus.valueOf(rs.getString("google_toll_status")), Rows.decimal(rs, "google_toll_estimate"),
                Rows.instant(rs, "google_computed_at"));
        BigDecimal pricePerLitre = Rows.decimal(rs, "fuel_price_per_l");
        PriceQuote price = pricePerLitre == null ? null
                : new PriceQuote(pricePerLitre, rs.getString("fuel_price_source"));
        return new Trip(rs.getLong("id"), rs.getLong("vehicle_id"), rs.getLong("driver_id"),
                Rows.date(rs, "trip_date"), Rows.time(rs, "start_time"), Rows.time(rs, "end_time"),
                Rows.longOrNull(rs, "origin_place_id"), Rows.longOrNull(rs, "destination_place_id"),
                rs.getInt("start_odometer_km"), Rows.intOrNull(rs, "end_odometer_km"), Rows.decimal(rs, "tolls_paid"),
                rs.getString("notes"), route, price);
    }
}
