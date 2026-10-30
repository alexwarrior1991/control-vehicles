package com.alejandro.controlvehicles.integration.fuel;

import com.alejandro.controlvehicles.domain.Country;
import com.alejandro.controlvehicles.domain.FuelType;
import com.alejandro.controlvehicles.domain.PriceSource;
import com.alejandro.controlvehicles.integration.HttpJson;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Prices of the Portuguese stations from DGEG ("Preços dos Combustíveis Online"). The service publishes no
 * history, so whatever the date asked it answers today's prices; the application stores them day by day. The
 * API is the one its own website uses, not a documented one: it is isolated here so a change only touches this
 * class.
 */
public final class PortugalDgegProvider implements FuelPriceProvider {

    public static final String DEFAULT_BASE_URL = "https://precoscombustiveis.dgeg.gov.pt/api/PrecoComb";
    /** "Gasolina simples 95" and "Gasóleo simples" in DGEG's catalogue. */
    static final int GASOLINE_95 = 3201;
    static final int DIESEL = 2101;

    private final String baseUrl;
    private final HttpJson http;

    public PortugalDgegProvider(String baseUrl, HttpJson http) {
        this.baseUrl = baseUrl;
        this.http = http;
    }

    @Override
    public Country country() {
        return Country.PT;
    }

    @Override
    public PriceSource source() {
        return PriceSource.DGEG;
    }

    @Override
    public boolean supportsHistory() {
        return false;
    }

    @Override
    public List<StationPrice> stationPrices(LocalDate date, FuelType fuelType) {
        int fuel = fuelType == FuelType.GASOLINE ? GASOLINE_95 : DIESEL;
        String url = baseUrl + "/PesquisarPostos?idsTiposComb=" + fuel
                + "&idMarca=&idTipoPosto=&idDistrito=&idsMunicipios=&qtdPorPagina=99999&pagina=1";
        return parse(http.get(url, Map.of()));
    }

    static List<StationPrice> parse(JsonNode response) {
        List<StationPrice> prices = new ArrayList<>();
        for (JsonNode node : response.path("resultado")) {
            BigDecimal price = Decimals.parse(node.path("Preco").asText(null));
            JsonNode latitude = node.path("Latitude");
            JsonNode longitude = node.path("Longitude");
            Double lat = latitude.isNumber() ? latitude.asDouble() : Decimals.coordinate(latitude.asText(null));
            Double lng = longitude.isNumber() ? longitude.asDouble() : Decimals.coordinate(longitude.asText(null));
            if (price == null || lat == null || lng == null) {
                continue;
            }
            prices.add(new StationPrice(node.path("Nome").asText(""), node.path("Marca").asText(""), lat, lng,
                    node.path("Localidade").asText(""), price));
        }
        return prices;
    }
}
