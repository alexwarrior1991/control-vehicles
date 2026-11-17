-- The drivers' phone app (repository control-vehicles-mobile): each driver's personal link carries a token, and a
-- trip pulled from the phone keeps the phone's id so that it is never imported twice.
ALTER TABLE driver ADD COLUMN mobile_token TEXT;
CREATE UNIQUE INDEX ux_driver_mobile_token ON driver (mobile_token);
ALTER TABLE trip ADD COLUMN mobile_id TEXT;
CREATE UNIQUE INDEX ux_trip_mobile_id ON trip (mobile_id);
