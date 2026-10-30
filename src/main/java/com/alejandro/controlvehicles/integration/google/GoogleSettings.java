package com.alejandro.controlvehicles.integration.google;

/** The key and the endpoints (overridable for tests). */
public record GoogleSettings(String apiKey, String placesBaseUrl, String routesBaseUrl, String languageCode) {

    public static GoogleSettings of(String apiKey) {
        return new GoogleSettings(apiKey, "https://places.googleapis.com", "https://routes.googleapis.com", "en");
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
