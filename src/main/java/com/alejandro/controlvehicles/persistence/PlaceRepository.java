package com.alejandro.controlvehicles.persistence;

import com.alejandro.controlvehicles.domain.Country;
import com.alejandro.controlvehicles.domain.Place;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class PlaceRepository {

    private final Database database;

    public PlaceRepository(Database database) {
        this.database = database;
    }

    public Optional<Place> findById(long id) {
        return database.read(c -> Sql.one(c, "SELECT * FROM place WHERE id = ?", PlaceRepository::map, id));
    }

    public List<Place> findAll() {
        return database.read(c -> Sql.list(c, "SELECT * FROM place ORDER BY name COLLATE NOCASE",
                PlaceRepository::map));
    }

    /** Stored places whose name or address contains the text, the most used first. */
    public List<Place> search(String text, int limit) {
        String like = "%" + text.trim().replace("%", "").replace("_", "") + "%";
        return database.read(c -> Sql.list(c, """
                        SELECT * FROM place
                        WHERE name LIKE ? OR address LIKE ?
                        ORDER BY use_count DESC, last_used_at DESC
                        LIMIT ?""",
                PlaceRepository::map, like, like, limit));
    }

    /** Stores a Google place once: a second save of the same place id updates it and keeps its id. */
    public Place save(Place place) {
        return database.transaction(c -> {
            if (place.id() != null) {
                Sql.update(c, """
                                UPDATE place SET google_place_id = ?, name = ?, address = ?, latitude = ?,
                                                 longitude = ?, country = ?
                                WHERE id = ?""",
                        place.googlePlaceId(), place.name(), place.address(), place.latitude(), place.longitude(),
                        place.country(), place.id());
                return place;
            }
            if (place.googlePlaceId() != null) {
                Optional<Long> existing = Sql.one(c, "SELECT id FROM place WHERE google_place_id = ?",
                        rs -> rs.getLong(1), place.googlePlaceId());
                if (existing.isPresent()) {
                    Place stored = place.withId(existing.get());
                    Sql.update(c, """
                                    UPDATE place SET name = ?, address = ?, latitude = ?, longitude = ?, country = ?
                                    WHERE id = ?""",
                            stored.name(), stored.address(), stored.latitude(), stored.longitude(), stored.country(),
                            stored.id());
                    return stored;
                }
            }
            long id = Sql.insert(c, """
                            INSERT INTO place (google_place_id, name, address, latitude, longitude, country)
                            VALUES (?, ?, ?, ?, ?, ?)""",
                    place.googlePlaceId(), place.name(), place.address(), place.latitude(), place.longitude(),
                    place.country());
            return place.withId(id);
        });
    }

    public void markUsed(long id) {
        database.transaction(c -> Sql.update(c,
                "UPDATE place SET use_count = use_count + 1, last_used_at = ? WHERE id = ?", Rows.now(), id));
    }

    private static Place map(ResultSet rs) throws SQLException {
        String country = rs.getString("country");
        return new Place(rs.getLong("id"), rs.getString("google_place_id"), rs.getString("name"),
                rs.getString("address"), Rows.doubleOrNull(rs, "latitude"), Rows.doubleOrNull(rs, "longitude"),
                country == null ? null : Country.valueOf(country));
    }
}
