package com.alejandro.controlvehicles.service;

import com.alejandro.controlvehicles.domain.Country;
import com.alejandro.controlvehicles.domain.FuelPrice;
import com.alejandro.controlvehicles.domain.FuelType;
import com.alejandro.controlvehicles.domain.Geo;
import com.alejandro.controlvehicles.domain.Place;
import com.alejandro.controlvehicles.domain.PriceQuote;
import com.alejandro.controlvehicles.domain.PriceStats;
import com.alejandro.controlvehicles.integration.ExternalApiException;
import com.alejandro.controlvehicles.integration.fuel.FuelPriceProvider;
import com.alejandro.controlvehicles.integration.fuel.StationPrice;
import com.alejandro.controlvehicles.persistence.FuelPriceRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The price of a litre for a trip, in this order: the preferred brand's (Galp) stations around the origin on the
 * trip's day; any station around it; the stored country price closest to that day; nothing (the trip editor then
 * asks for a manual one). Every successful station query also stores the country-wide average of that day, which
 * becomes the history used when the service cannot answer for a date (Portugal has no history).
 */
public final class FuelPriceService {

    private final Map<Country, FuelPriceProvider> providers = new EnumMap<>(Country.class);
    private final FuelPriceRepository repository;
    private final Supplier<AppSettings> settings;
    private final Clock clock;

    public FuelPriceService(List<FuelPriceProvider> providers, FuelPriceRepository repository,
                            Supplier<AppSettings> settings, Clock clock) {
        providers.forEach(provider -> this.providers.put(provider.country(), provider));
        this.repository = repository;
        this.settings = settings;
        this.clock = clock;
    }

    public Optional<PriceQuote> quote(Place origin, LocalDate date, FuelType fuelType) {
        AppSettings rules = settings.get();
        Country country = origin != null && origin.country() != null ? origin.country() : rules.defaultCountry();
        FuelPriceProvider provider = providers.get(country);
        boolean live = provider != null && !date.isAfter(today())
                && (provider.supportsHistory() || date.equals(today()));
        if (live && origin != null && origin.hasLocation()) {
            try {
                List<StationPrice> stations = provider.stationPrices(date, fuelType);
                store(date, country, fuelType, provider, stations, rules.preferredBrand());
                Optional<PriceQuote> nearby = nearby(origin, stations, rules, provider, fuelType);
                if (nearby.isPresent()) {
                    return nearby;
                }
            } catch (ExternalApiException unavailable) {
                // Sin servicio se sigue con el histórico guardado.
            }
        }
        return repository.closest(date, country, fuelType, rules.preferredBrand()).map(price -> new PriceQuote(
                price.averagePrice(), describe(price, date)));
    }

    /** Today's country-wide prices of both fuels, stored; what the "Fetch today's prices" button does. */
    public List<FuelPrice> fetchToday(Country country) {
        FuelPriceProvider provider = providers.get(country);
        if (provider == null) {
            throw new ValidationException("There is no price service for " + country.label());
        }
        List<FuelPrice> stored = new ArrayList<>();
        for (FuelType fuelType : FuelType.values()) {
            stored.addAll(store(today(), country, fuelType, provider, provider.stationPrices(today(), fuelType),
                    settings.get().preferredBrand()));
        }
        return stored;
    }

    public List<FuelPrice> all() {
        return repository.findAll();
    }

    public FuelPrice saveManual(FuelPrice price) {
        return repository.upsert(price);
    }

    public void delete(long id) {
        repository.delete(id);
    }

    private Optional<PriceQuote> nearby(Place origin, List<StationPrice> stations, AppSettings rules,
                                        FuelPriceProvider provider, FuelType fuelType) {
        List<StationPrice> around = stations.stream()
                .filter(s -> Geo.distanceKm(origin.latitude(), origin.longitude(), s.latitude(), s.longitude())
                        <= rules.priceRadiusKm())
                .toList();
        String brand = rules.preferredBrand();
        List<StationPrice> branded = brand == null ? List.of() : around.stream().filter(s -> s.isBrand(brand)).toList();
        List<StationPrice> chosen = branded.isEmpty() ? around : branded;
        return PriceStats.of(chosen.stream().map(StationPrice::price).toList()).map(stats -> new PriceQuote(
                stats.average(), String.format(Locale.ENGLISH, "%s %s avg of %d %s within %d km", provider.source(),
                fuelType.label(), stats.count(), branded.isEmpty() ? "stations" : brand + " stations",
                rules.priceRadiusKm())));
    }

    private List<FuelPrice> store(LocalDate date, Country country, FuelType fuelType, FuelPriceProvider provider,
                                  List<StationPrice> stations, String brand) {
        List<FuelPrice> stored = new ArrayList<>();
        Instant now = clock.instant();
        PriceStats.of(stations.stream().map(StationPrice::price).toList()).ifPresent(stats -> stored.add(
                repository.upsert(new FuelPrice(null, date, country, fuelType, null, stats.average(), stats.min(),
                        stats.max(), stats.count(), provider.source(), now))));
        if (brand != null) {
            PriceStats.of(stations.stream().filter(s -> s.isBrand(brand)).map(StationPrice::price).toList())
                    .ifPresent(stats -> stored.add(repository.upsert(new FuelPrice(null, date, country, fuelType,
                            brand, stats.average(), stats.min(), stats.max(), stats.count(), provider.source(),
                            now))));
        }
        return stored;
    }

    private static String describe(FuelPrice price, LocalDate wanted) {
        String day = price.date().equals(wanted) ? "" : " of " + price.date();
        return String.format(Locale.ENGLISH, "%s %s %s country avg%s", price.source(), price.country().name(),
                price.brand() == null ? "all brands" : price.brand(), day);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
