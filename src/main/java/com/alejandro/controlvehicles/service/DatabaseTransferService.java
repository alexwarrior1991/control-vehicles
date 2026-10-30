package com.alejandro.controlvehicles.service;

import com.alejandro.controlvehicles.persistence.Database;
import com.alejandro.controlvehicles.persistence.MigrationRunner;
import org.sqlite.SQLiteConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Handing the data to someone else: the export is one compact .db file written by SQLite itself
 * ({@code VACUUM INTO}), consistent even while the application runs; the import checks the file is a
 * control-vehicles database, backs up the current one next to it and replaces it. The Google key is not in the
 * database, so it never travels.
 */
public final class DatabaseTransferService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Database database;
    private final Clock clock;

    public DatabaseTransferService(Database database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    public void exportTo(Path target) {
        Path absolute = target.toAbsolutePath();
        if (absolute.equals(database.file())) {
            throw new ValidationException("Choose a file other than the database in use");
        }
        try {
            Files.deleteIfExists(absolute);
        } catch (IOException e) {
            throw new ValidationException("Cannot replace " + absolute + ": " + e.getMessage());
        }
        database.executeAutoCommit("VACUUM INTO '" + absolute.toString().replace("'", "''") + "'");
    }

    /** Replaces the database with the file; returns where the previous one was backed up. */
    public Path importFrom(Path source) {
        Path absolute = source.toAbsolutePath();
        if (absolute.equals(database.file())) {
            throw new ValidationException("That file is already the database in use");
        }
        check(absolute);
        Path backup = database.file().resolveSibling(database.file().getFileName().toString().replace(".db", "")
                + "-backup-" + STAMP.format(LocalDateTime.now(clock)) + ".db");
        try {
            if (Files.exists(database.file())) {
                exportTo(backup);
            }
            Files.copy(absolute, database.file(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ValidationException("Cannot copy " + absolute + ": " + e.getMessage());
        }
        database.initialize();
        return backup;
    }

    private static void check(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new ValidationException(file + " is not a file");
        }
        SQLiteConfig readOnly = new SQLiteConfig();
        readOnly.setReadOnly(true);
        try (Connection connection = readOnly.createConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement()) {
            int applicationId;
            try (ResultSet rs = statement.executeQuery("PRAGMA application_id")) {
                applicationId = rs.next() ? rs.getInt(1) : 0;
            }
            if (applicationId != Database.APPLICATION_ID) {
                throw new ValidationException(file.getFileName() + " is not a control-vehicles database");
            }
            int version;
            try (ResultSet rs = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
                version = rs.next() ? rs.getInt(1) : 0;
            }
            if (version > MigrationRunner.latestVersion()) {
                throw new ValidationException(file.getFileName()
                        + " comes from a newer version of control-vehicles: update the application first");
            }
        } catch (SQLException e) {
            throw new ValidationException(file.getFileName() + " is not a readable SQLite database");
        }
    }
}
