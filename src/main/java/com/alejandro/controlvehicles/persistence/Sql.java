package com.alejandro.controlvehicles.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The four shapes of statement the repositories need. */
final class Sql {

    @FunctionalInterface
    interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private Sql() {
    }

    static <T> List<T> list(Connection connection, String sql, RowMapper<T> mapper, Object... params)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            Rows.bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapper.map(rs));
                }
                return result;
            }
        }
    }

    static <T> Optional<T> one(Connection connection, String sql, RowMapper<T> mapper, Object... params)
            throws SQLException {
        List<T> rows = list(connection, sql, mapper, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    static int update(Connection connection, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            Rows.bind(ps, params);
            return ps.executeUpdate();
        }
    }

    static long insert(Connection connection, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql, Rows.returnGeneratedKeys())) {
            Rows.bind(ps, params);
            ps.executeUpdate();
            return Rows.generatedId(ps);
        }
    }
}
