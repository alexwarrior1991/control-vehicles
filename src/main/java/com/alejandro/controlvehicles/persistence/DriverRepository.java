package com.alejandro.controlvehicles.persistence;

import com.alejandro.controlvehicles.domain.Driver;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class DriverRepository {

    private final Database database;

    public DriverRepository(Database database) {
        this.database = database;
    }

    public List<Driver> findAll() {
        return database.read(c -> Sql.list(c, "SELECT * FROM driver ORDER BY name COLLATE NOCASE, id",
                DriverRepository::map));
    }

    public Optional<Driver> findById(long id) {
        return database.read(c -> Sql.one(c, "SELECT * FROM driver WHERE id = ?", DriverRepository::map, id));
    }

    public Driver save(Driver driver) {
        return database.transaction(c -> {
            String now = Rows.now();
            if (driver.id() == null) {
                long id = Sql.insert(c, """
                                INSERT INTO driver (name, licence_number, phone, email, active, notes, created_at,
                                                    updated_at)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                        driver.name(), driver.licenceNumber(), driver.phone(), driver.email(), driver.active(),
                        driver.notes(), now, now);
                return driver.withId(id);
            }
            Sql.update(c, """
                            UPDATE driver SET name = ?, licence_number = ?, phone = ?, email = ?, active = ?,
                                              notes = ?, updated_at = ?
                            WHERE id = ?""",
                    driver.name(), driver.licenceNumber(), driver.phone(), driver.email(), driver.active(),
                    driver.notes(), now, driver.id());
            return driver;
        });
    }

    public void delete(long id) {
        database.transaction(c -> Sql.update(c, "DELETE FROM driver WHERE id = ?", id));
    }

    private static Driver map(ResultSet rs) throws SQLException {
        return new Driver(rs.getLong("id"), rs.getString("name"), rs.getString("licence_number"),
                rs.getString("phone"), rs.getString("email"), rs.getInt("active") == 1, rs.getString("notes"));
    }
}
