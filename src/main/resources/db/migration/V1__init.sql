-- Decimals are stored as TEXT (BigDecimal.toPlainString) so money never goes through floating point.
-- Dates and times are ISO-8601 TEXT.

CREATE TABLE driver (
    id             INTEGER PRIMARY KEY,
    name           TEXT    NOT NULL,
    licence_number TEXT,
    phone          TEXT,
    email          TEXT,
    active         INTEGER NOT NULL DEFAULT 1,
    notes          TEXT,
    created_at     TEXT    NOT NULL,
    updated_at     TEXT    NOT NULL
);

CREATE TABLE vehicle (
    id                      INTEGER PRIMARY KEY,
    plate                   TEXT    NOT NULL UNIQUE,
    brand                   TEXT,
    model                   TEXT,
    fuel_type               TEXT    NOT NULL CHECK (fuel_type IN ('GASOLINE', 'DIESEL')),
    nominal_consumption_l100 TEXT   NOT NULL,
    tank_capacity_l         TEXT,
    initial_odometer_km     INTEGER NOT NULL DEFAULT 0,
    active                  INTEGER NOT NULL DEFAULT 1,
    notes                   TEXT,
    created_at              TEXT    NOT NULL,
    updated_at              TEXT    NOT NULL
);

CREATE TABLE place (
    id              INTEGER PRIMARY KEY,
    google_place_id TEXT UNIQUE,
    name            TEXT    NOT NULL,
    address         TEXT,
    latitude        REAL,
    longitude       REAL,
    country         TEXT,
    use_count       INTEGER NOT NULL DEFAULT 0,
    last_used_at    TEXT
);

CREATE TABLE trip (
    id                   INTEGER PRIMARY KEY,
    vehicle_id           INTEGER NOT NULL REFERENCES vehicle (id),
    driver_id            INTEGER NOT NULL REFERENCES driver (id),
    trip_date            TEXT    NOT NULL,
    start_time           TEXT    NOT NULL,
    end_time             TEXT    NOT NULL,
    origin_place_id      INTEGER REFERENCES place (id),
    destination_place_id INTEGER REFERENCES place (id),
    start_odometer_km    INTEGER NOT NULL,
    tolls_paid           TEXT    NOT NULL DEFAULT '0',
    notes                TEXT,
    google_distance_m    INTEGER,
    google_duration_s    INTEGER,
    google_toll_status   TEXT CHECK (google_toll_status IN ('NONE', 'PRICED', 'UNPRICED')),
    google_toll_estimate TEXT,
    google_computed_at   TEXT,
    fuel_price_per_l     TEXT,
    fuel_price_source    TEXT,
    created_at           TEXT    NOT NULL,
    updated_at           TEXT    NOT NULL
);

CREATE INDEX ix_trip_vehicle_start ON trip (vehicle_id, trip_date, start_time);
CREATE INDEX ix_trip_driver_start ON trip (driver_id, trip_date, start_time);

CREATE TABLE refuel (
    id           INTEGER PRIMARY KEY,
    vehicle_id   INTEGER NOT NULL REFERENCES vehicle (id),
    driver_id    INTEGER REFERENCES driver (id),
    refuel_at    TEXT    NOT NULL,
    odometer_km  INTEGER NOT NULL,
    litres       TEXT    NOT NULL,
    total_amount TEXT    NOT NULL,
    full_tank    INTEGER NOT NULL DEFAULT 1,
    station      TEXT,
    brand        TEXT,
    country      TEXT,
    notes        TEXT,
    created_at   TEXT    NOT NULL,
    updated_at   TEXT    NOT NULL
);

CREATE INDEX ix_refuel_vehicle_at ON refuel (vehicle_id, refuel_at);

-- brand is '' for "every brand" so that the unique key also holds for it (NULLs never collide in SQLite).
CREATE TABLE fuel_price (
    id            INTEGER PRIMARY KEY,
    price_date    TEXT    NOT NULL,
    country       TEXT    NOT NULL,
    fuel_type     TEXT    NOT NULL CHECK (fuel_type IN ('GASOLINE', 'DIESEL')),
    brand         TEXT    NOT NULL DEFAULT '',
    average_price TEXT    NOT NULL,
    min_price     TEXT    NOT NULL,
    max_price     TEXT    NOT NULL,
    stations      INTEGER NOT NULL DEFAULT 0,
    source        TEXT    NOT NULL CHECK (source IN ('MITECO', 'DGEG', 'MANUAL')),
    fetched_at    TEXT    NOT NULL,
    UNIQUE (price_date, country, fuel_type, brand, source)
);

CREATE TABLE app_setting (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
