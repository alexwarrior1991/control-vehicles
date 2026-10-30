package com.alejandro.controlvehicles.integration.fuel;

import com.alejandro.controlvehicles.domain.Country;
import com.alejandro.controlvehicles.domain.FuelType;
import com.alejandro.controlvehicles.domain.PriceSource;
import com.alejandro.controlvehicles.integration.HttpJson;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prices of every Spanish station from the Ministry (MITECO), free and without a key: today's list, or the
 * historical one of a past day. One answer brings both fuels for the whole country (about 12,000 stations), so
 * it is kept in memory per day while the application runs.
 */
public final class SpainMitecoProvider implements FuelPriceProvider {

    public static final String DEFAULT_BASE_URL =
            "https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes";
    private static final DateTimeFormatter HISTORY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private record Station(String name, String brand, double latitude, double longitude, String locality,
                           BigDecimal gasoline, BigDecimal diesel) {
    }

    private final String baseUrl;
    private final HttpJson http;
    private final Clock clock;
    private final Map<LocalDate, List<Station>> cache = new ConcurrentHashMap<>();

    public SpainMitecoProvider(String baseUrl, HttpJson http, Clock clock) {
        this.baseUrl = baseUrl;
        this.http = http;
        this.clock = clock;
    }

    @Override
    public Country country() {
        return Country.ES;
    }

    @Override
    public PriceSource source() {
        return PriceSource.MITECO;
    }

    @Override
    public boolean supportsHistory() {
        return true;
    }

    @Override
    public List<StationPrice> stationPrices(LocalDate date, FuelType fuelType) {
        List<StationPrice> prices = new ArrayList<>();
        for (Station station : cache.computeIfAbsent(date, this::fetch)) {
            BigDecimal price = fuelType == FuelType.GASOLINE ? station.gasoline() : station.diesel();
            if (price != null) {
                prices.add(new StationPrice(station.name(), station.brand(), station.latitude(), station.longitude(),
                        station.locality(), price));
            }
        }
        return prices;
    }

    private List<Station> fetch(LocalDate date) {
        String url = date.isBefore(LocalDate.now(clock))
                ? baseUrl + "/EstacionesTerrestresHist/" + HISTORY_DATE.format(date)
                : baseUrl + "/EstacionesTerrestres/";
        return parse(http.get(url, Map.of()));
    }

    static List<Station> parse(JsonNode response) {
        List<Station> stations = new ArrayList<>();
        for (JsonNode node : response.path("ListaEESSPrecio")) {
            Double latitude = Decimals.coordinate(node.path("Latitud").asText(null));
            Double longitude = Decimals.coordinate(node.path("Longitud (WGS84)").asText(null));
            if (latitude == null || longitude == null) {
                continue;
            }
            stations.add(new Station(node.path("Dirección").asText(""), node.path("Rótulo").asText(""), latitude,
                    longitude, node.path("Localidad").asText(""),
                    Decimals.parse(node.path("Precio Gasolina 95 E5").asText(null)),
                    Decimals.parse(node.path("Precio Gasoleo A").asText(null))));
        }
        return stations;
    }
}
