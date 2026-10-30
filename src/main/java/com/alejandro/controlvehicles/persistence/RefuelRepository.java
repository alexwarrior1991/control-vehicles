package com.alejandro.controlvehicles.persistence;

import com.alejandro.controlvehicles.domain.Country;
import com.alejandro.controlvehicles.domain.Refuel;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class RefuelRepository {

    private final Database database;

    public RefuelRepository(Database database) {
        this.database = database;
    }

    public List<Refuel> findAll() {
        return database.read(c -> Sql.list(c, "SELECT * FROM refuel ORDER BY refuel_at, id", RefuelRepository::map));
    }

    public Optional<Refuel> findById(long id) {
        return database.read(c -> Sql.one(c, "SELECT * FROM refuel WHERE id = ?", RefuelRepository::map, id));
    }

    public Refuel save(Refuel refuel) {
        Object[] values = {refuel.vehicleId(), refuel.driverId(), refuel.refuelAt(), refuel.odometerKm(),
                refuel.litres(), refuel.totalAmount(), refuel.fullTank(), refuel.station(), refuel.brand(),
                refuel.country(), refuel.notes()};
        return database.transaction(c -> {
            String now = Rows.now();
            if (refuel.id() == null) {
                long id = Sql.insert(c, """
                                INSERT INTO refuel (vehicle_id, driver_id, refuel_at, odometer_km, litres,
                                                    total_amount, full_tank, station, brand, country, notes,
                                                    created_at, updated_at)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                        values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7],
                        values[8], values[9], values[10], now, now);
                return refuel.withId(id);
            }
            Sql.update(c, """
                            UPDATE refuel SET vehicle_id = ?, driver_id = ?, refuel_at = ?, odometer_km = ?,
                                              litres = ?, total_amount = ?, full_tank = ?, station = ?, brand = ?,
                                              country = ?, notes = ?, updated_at = ?
                            WHERE id = ?""",
                    values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7],
                    values[8], values[9], values[10], now, refuel.id());
            return refuel;
        });
    }

    public void delete(long id) {
        database.transaction(c -> Sql.update(c, "DELETE FROM refuel WHERE id = ?", id));
    }

    private static Refuel map(ResultSet rs) throws SQLException {
        String country = rs.getString("country");
        return new Refuel(rs.getLong("id"), rs.getLong("vehicle_id"), Rows.longOrNull(rs, "driver_id"),
                Rows.dateTime(rs, "refuel_at"), rs.getInt("odometer_km"), Rows.decimal(rs, "litres"),
                Rows.decimal(rs, "total_amount"), rs.getInt("full_tank") == 1, rs.getString("station"),
                rs.getString("brand"), country == null ? null : Country.valueOf(country), rs.getString("notes"));
    }
}
