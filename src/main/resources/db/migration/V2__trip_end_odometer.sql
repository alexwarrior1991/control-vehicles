-- The odometer at the end of a trip, when the driver notes it: it separates the trip's own distance from the
-- use nobody registered before the next trip. Optional, so every trip already stored stays valid.
ALTER TABLE trip ADD COLUMN end_odometer_km INTEGER CHECK (end_odometer_km IS NULL OR end_odometer_km >= start_odometer_km);
