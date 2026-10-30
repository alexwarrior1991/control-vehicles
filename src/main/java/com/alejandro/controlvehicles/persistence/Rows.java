package com.alejandro.controlvehicles.persistence;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Reading and binding the column types this schema uses (decimals and dates as TEXT). */
final class Rows {

    private Rows() {
    }

    static BigDecimal decimal(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : new BigDecimal(value);
    }

    static LocalDate date(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : LocalDate.parse(value);
    }

    static LocalTime time(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : LocalTime.parse(value);
    }

    static LocalDateTime dateTime(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : LocalDateTime.parse(value);
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : Instant.parse(value);
    }

    static Long longOrNull(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    static Integer intOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    static Double doubleOrNull(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    static void set(PreparedStatement ps, int index, Object value) throws SQLException {
        switch (value) {
            case null -> ps.setNull(index, Types.NULL);
            case BigDecimal decimal -> ps.setString(index, decimal.toPlainString());
            case Boolean bool -> ps.setInt(index, bool ? 1 : 0);
            case Integer number -> ps.setInt(index, number);
            case Long number -> ps.setLong(index, number);
            case Double number -> ps.setDouble(index, number);
            case Enum<?> constant -> ps.setString(index, constant.name());
            default -> ps.setString(index, value.toString());
        }
    }

    static void bind(PreparedStatement ps, Object... values) throws SQLException {
        for (int i = 0; i < values.length; i++) {
            set(ps, i + 1, values[i]);
        }
    }

    static long generatedId(PreparedStatement ps) throws SQLException {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            if (!keys.next()) {
                throw new SQLException("No id generated");
            }
            return keys.getLong(1);
        }
    }

    static String now() {
        return Instant.now().toString();
    }

    static int returnGeneratedKeys() {
        return Statement.RETURN_GENERATED_KEYS;
    }
}
