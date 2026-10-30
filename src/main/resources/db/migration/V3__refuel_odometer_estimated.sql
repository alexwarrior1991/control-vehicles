-- A refuel imported from a card statement without the odometer gets an estimated one, marked here: it never sets a
-- consumption boundary nor a km check until someone types the real reading.
ALTER TABLE refuel ADD COLUMN odometer_estimated INTEGER NOT NULL DEFAULT 0;
