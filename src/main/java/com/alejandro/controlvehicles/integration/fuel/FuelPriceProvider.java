package com.alejandro.controlvehicles.integration.fuel;

import com.alejandro.controlvehicles.domain.Country;
import com.alejandro.controlvehicles.domain.FuelType;
import com.alejandro.controlvehicles.domain.PriceSource;

import java.time.LocalDate;
import java.util.List;

/** The station prices of one country. */
public interface FuelPriceProvider {

    Country country();

    PriceSource source();

    /** Whether it can tell the prices of a past day, not only today's. */
    boolean supportsHistory();

    /** Every station with a price for that fuel on that day (today's when there is no history). */
    List<StationPrice> stationPrices(LocalDate date, FuelType fuelType);
}
