package com.alejandro.controlvehicles.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * The SQLite file. A connection is opened per operation and closed right after: nothing stays open, so the
 * file can be copied or replaced at any moment (export and import), and the journal stays the classic rollback
 * one (no WAL), so the database is always a single self-contained file.
 */
public final class Database {

    /** "CVH1": what {@code PRAGMA application_id} says in a control-vehicles database. */
    public static final int APPLICATION_ID = 0x43564831;

    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private final Path file;

    public Database(Path file) {
        this.file = file.toAbsolutePath();
    }

    public Path file() {
        return file;
    }

    /** Creates the folder, the file and every pending migration. */
    public Database initialize() {
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            throw new DataAccessException("Cannot create " + file.getParent(), e);
        }
        new MigrationRunner(this).migrate();
        return this;
    }

    public <T> T read(SqlWork<T> work) {
        try (Connection connection = open()) {
            return work.run(connection);
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    public <T> T transaction(SqlWork<T> work) {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    /** A statement SQLite refuses inside a transaction ({@code VACUUM}). */
    public void executeAutoCommit(String sql) {
        read(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
            return null;
        });
    }

    Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    private static DataAccessException translate(SQLException e) {
        String message = e.getMessage() == null ? "Database error" : e.getMessage();
        if (message.contains("FOREIGN KEY constraint failed")) {
            return new DataAccessException("It is still used by trips or refuels: deactivate it instead.", e);
        }
        if (message.contains("UNIQUE constraint failed")) {
            return new DataAccessException("There is already one with the same "
                    + message.substring(message.lastIndexOf('.') + 1).replace(")", "") + ".", e);
        }
        return new DataAccessException(message, e);
    }
}
