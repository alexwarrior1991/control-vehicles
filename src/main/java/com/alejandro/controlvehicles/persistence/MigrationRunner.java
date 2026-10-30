package com.alejandro.controlvehicles.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies {@code db/migration/V<n>__<description>.sql} in order, once each, recording them in
 * {@code schema_version}. A new schema change is always a new file appended to {@link #MIGRATIONS}: an
 * exported database from an older version is migrated when it is imported.
 */
public final class MigrationRunner {

    /** Every migration, in order. The version is the index + 1. */
    public static final List<String> MIGRATIONS = List.of("V1__init.sql", "V2__trip_end_odometer.sql",
            "V3__refuel_odometer_estimated.sql");

    private final Database database;

    MigrationRunner(Database database) {
        this.database = database;
    }

    public static int latestVersion() {
        return MIGRATIONS.size();
    }

    void migrate() {
        database.transaction(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS schema_version (
                            version     INTEGER PRIMARY KEY,
                            description TEXT NOT NULL,
                            applied_at  TEXT NOT NULL
                        )""");
                statement.execute("PRAGMA application_id = " + Database.APPLICATION_ID);
            }
            int current = currentVersion(connection);
            for (int version = current + 1; version <= MIGRATIONS.size(); version++) {
                String name = MIGRATIONS.get(version - 1);
                try (Statement statement = connection.createStatement()) {
                    for (String sql : statements(read(name))) {
                        statement.execute(sql);
                    }
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO schema_version (version, description, applied_at) VALUES (?, ?, ?)")) {
                    insert.setInt(1, version);
                    insert.setString(2, name);
                    insert.setString(3, Instant.now().toString());
                    insert.executeUpdate();
                }
            }
            return null;
        });
    }

    static int currentVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static String read(String name) {
        try (InputStream in = MigrationRunner.class.getResourceAsStream("/db/migration/" + name)) {
            if (in == null) {
                throw new DataAccessException("Missing migration " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DataAccessException("Cannot read migration " + name, e);
        }
    }

    /** Splits a script on the semicolons that end a line, dropping the comment lines. */
    static List<String> statements(String script) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String sql = current.toString().trim();
                result.add(sql.substring(0, sql.length() - 1));
                current.setLength(0);
            }
        }
        if (!current.toString().isBlank()) {
            result.add(current.toString().trim());
        }
        return result;
    }
}
