package com.alejandro.controlvehicles.persistence;

import java.util.HashMap;
import java.util.Map;

/** The settings that travel with the database (thresholds, search radius): never a secret. */
public final class SettingRepository {

    private final Database database;

    public SettingRepository(Database database) {
        this.database = database;
    }

    public Map<String, String> findAll() {
        return database.read(c -> {
            Map<String, String> values = new HashMap<>();
            Sql.list(c, "SELECT key, value FROM app_setting", rs -> values.put(rs.getString(1), rs.getString(2)));
            return values;
        });
    }

    public void saveAll(Map<String, String> values) {
        database.transaction(c -> {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                Sql.update(c, """
                                INSERT INTO app_setting (key, value) VALUES (?, ?)
                                ON CONFLICT (key) DO UPDATE SET value = excluded.value""",
                        entry.getKey(), entry.getValue());
            }
            return null;
        });
    }
}
