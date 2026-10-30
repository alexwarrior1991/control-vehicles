package com.alejandro.controlvehicles.persistence;

import com.alejandro.controlvehicles.domain.FuelType;
import com.alejandro.controlvehicles.domain.Vehicle;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class VehicleRepository {

    private final Database database;

    public VehicleRepository(Database database) {
        this.database = database;
    }

    public List<Vehicle> findAll() {
        return database.read(c -> Sql.list(c, "SELECT * FROM vehicle ORDER BY plate, id", VehicleRepository::map));
    }

    public Optional<Vehicle> findById(long id) {
        return database.read(c -> Sql.one(c, "SELECT * FROM vehicle WHERE id = ?", VehicleRepository::map, id));
    }

    public Vehicle save(Vehicle vehicle) {
        return database.transaction(c -> {
            String now = Rows.now();
            if (vehicle.id() == null) {
                long id = Sql.insert(c, """
                                INSERT INTO vehicle (plate, brand, model, fuel_type, nominal_consumption_l100,
                                                     tank_capacity_l, initial_odometer_km, active, notes, created_at,
                                                     updated_at)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                        vehicle.plate(), vehicle.brand(), vehicle.model(), vehicle.fuelType(),
                        vehicle.nominalConsumptionL100(), vehicle.tankCapacityL(), vehicle.initialOdometerKm(),
                        vehicle.active(), vehicle.notes(), now, now);
                return vehicle.withId(id);
            }
            Sql.update(c, """
                            UPDATE vehicle SET plate = ?, brand = ?, model = ?, fuel_type = ?,
                                               nominal_consumption_l100 = ?, tank_capacity_l = ?,
                                               initial_odometer_km = ?, active = ?, notes = ?, updated_at = ?
                            WHERE id = ?""",
                    vehicle.plate(), vehicle.brand(), vehicle.model(), vehicle.fuelType(),
                    vehicle.nominalConsumptionL100(), vehicle.tankCapacityL(), vehicle.initialOdometerKm(),
                    vehicle.active(), vehicle.notes(), now, vehicle.id());
            return vehicle;
        });
    }

    public void delete(long id) {
        database.transaction(c -> Sql.update(c, "DELETE FROM vehicle WHERE id = ?", id));
    }

    private static Vehicle map(ResultSet rs) throws SQLException {
        return new Vehicle(rs.getLong("id"), rs.getString("plate"), rs.getString("brand"), rs.getString("model"),
                FuelType.valueOf(rs.getString("fuel_type")), Rows.decimal(rs, "nominal_consumption_l100"),
                Rows.decimal(rs, "tank_capacity_l"), rs.getInt("initial_odometer_km"), rs.getInt("active") == 1,
                rs.getString("notes"));
    }
}
