package com.alejandro.controlvehicles.persistence;

import com.alejandro.controlvehicles.domain.Country;
import com.alejandro.controlvehicles.domain.FuelPrice;
import com.alejandro.controlvehicles.domain.FuelType;
import com.alejandro.controlvehicles.domain.PriceSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public final class FuelPriceRepository {

    private final Database database;

    public FuelPriceRepository(Database database) {
        this.database = database;
    }

    public List<FuelPrice> findAll() {
        return database.read(c -> Sql.list(c,
                "SELECT * FROM fuel_price ORDER BY price_date DESC, country, fuel_type, brand, source",
                FuelPriceRepository::map));
    }

    /** Inserts or replaces the price of that day, country, fuel, brand and source. */
    public FuelPrice upsert(FuelPrice price) {
        return database.transaction(c -> {
            long id = Sql.one(c, """
                            INSERT INTO fuel_price (price_date, country, fuel_type, brand, average_price, min_price,
                                                    max_price, stations, source, fetched_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            ON CONFLICT (price_date, country, fuel_type, brand, source) DO UPDATE SET
                                average_price = excluded.average_price, min_price = excluded.min_price,
                                max_price = excluded.max_price, stations = excluded.stations,
                                fetched_at = excluded.fetched_at
                            RETURNING id""",
                    rs -> rs.getLong(1), price.date(), price.country(), price.fuelType(),
                    price.brand() == null ? "" : price.brand(), price.averagePrice(), price.minPrice(),
                    price.maxPrice(), price.stations(), price.source(), price.fetchedAt()).orElseThrow();
            return new FuelPrice(id, price.date(), price.country(), price.fuelType(), price.brand(),
                    price.averagePrice(), price.minPrice(), price.maxPrice(), price.stations(), price.source(),
                    price.fetchedAt());
        });
    }

    /**
     * The stored price closest to the date (on or before it first, then after it), preferring the brand and, on
     * the same day, a manual price over a fetched one.
     */
    public Optional<FuelPrice> closest(LocalDate date, Country country, FuelType fuelType, String preferredBrand) {
        String brand = preferredBrand == null ? "" : preferredBrand;
        return database.read(c -> Sql.one(c, """
                        SELECT * FROM fuel_price
                        WHERE country = ? AND fuel_type = ? AND (brand = ? OR brand = '')
                        ORDER BY CASE WHEN brand = ? THEN 0 ELSE 1 END,
                                 ABS(julianday(price_date) - julianday(?)),
                                 CASE WHEN price_date <= ? THEN 0 ELSE 1 END,
                                 CASE WHEN source = 'MANUAL' THEN 0 ELSE 1 END
                        LIMIT 1""",
                FuelPriceRepository::map, country, fuelType, brand, brand, date, date));
    }

    public void delete(long id) {
        database.transaction(c -> Sql.update(c, "DELETE FROM fuel_price WHERE id = ?", id));
    }

    private static FuelPrice map(ResultSet rs) throws SQLException {
        String brand = rs.getString("brand");
        return new FuelPrice(rs.getLong("id"), Rows.date(rs, "price_date"), Country.valueOf(rs.getString("country")),
                FuelType.valueOf(rs.getString("fuel_type")), brand.isEmpty() ? null : brand,
                Rows.decimal(rs, "average_price"), Rows.decimal(rs, "min_price"), Rows.decimal(rs, "max_price"),
                rs.getInt("stations"), PriceSource.valueOf(rs.getString("source")), Rows.instant(rs, "fetched_at"));
    }
}
