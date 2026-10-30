package com.alejandro.controlvehicles.integration.google;

import com.alejandro.controlvehicles.domain.Country;
import com.alejandro.controlvehicles.domain.Place;
import com.alejandro.controlvehicles.integration.ExternalApiException;
import com.alejandro.controlvehicles.integration.HttpJson;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Places API (New): autocomplete restricted to Spain and Portugal, then the details of the chosen suggestion.
 * Both calls carry the same session token, which Google bills as one session.
 */
public final class PlacesClient {

    private final Supplier<GoogleSettings> settingsSource;
    private final HttpJson http;

    public PlacesClient(Supplier<GoogleSettings> settingsSource, HttpJson http) {
        this.settingsSource = settingsSource;
        this.http = http;
    }

    public List<PlaceSuggestion> autocomplete(String input, String sessionToken) {
        GoogleSettings settings = requireKey();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input", input);
        body.put("sessionToken", sessionToken);
        body.put("includedRegionCodes", List.of("es", "pt"));
        body.put("languageCode", settings.languageCode());
        JsonNode response = http.post(settings.placesBaseUrl() + "/v1/places:autocomplete",
                Map.of("X-Goog-Api-Key", settings.apiKey()), body);
        List<PlaceSuggestion> suggestions = new ArrayList<>();
        for (JsonNode suggestion : response.path("suggestions")) {
            JsonNode prediction = suggestion.path("placePrediction");
            if (prediction.isMissingNode()) {
                continue;
            }
            JsonNode format = prediction.path("structuredFormat");
            String main = format.path("mainText").path("text").asText(prediction.path("text").path("text").asText());
            String secondary = format.path("secondaryText").path("text").asText(null);
            suggestions.add(new PlaceSuggestion(prediction.path("placeId").asText(), main, secondary));
        }
        return suggestions;
    }

    public Place details(String placeId, String sessionToken) {
        GoogleSettings settings = requireKey();
        String url = settings.placesBaseUrl() + "/v1/places/" + encode(placeId) + "?languageCode="
                + encode(settings.languageCode())
                + (sessionToken == null ? "" : "&sessionToken=" + encode(sessionToken));
        JsonNode place = http.get(url, Map.of("X-Goog-Api-Key", settings.apiKey(),
                "X-Goog-FieldMask", "id,displayName,formattedAddress,location,addressComponents"));
        Country country = null;
        for (JsonNode component : place.path("addressComponents")) {
            for (JsonNode type : component.path("types")) {
                if ("country".equals(type.asText())) {
                    country = Country.fromIsoCode(component.path("shortText").asText()).orElse(null);
                }
            }
        }
        JsonNode location = place.path("location");
        return new Place(null, place.path("id").asText(placeId),
                place.path("displayName").path("text").asText(place.path("formattedAddress").asText(placeId)),
                place.path("formattedAddress").asText(null),
                location.has("latitude") ? location.get("latitude").asDouble() : null,
                location.has("longitude") ? location.get("longitude").asDouble() : null, country);
    }

    private GoogleSettings requireKey() {
        GoogleSettings settings = settingsSource.get();
        if (!settings.configured()) {
            throw new ExternalApiException("No Google API key: set it in Settings", 0);
        }
        return settings;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
