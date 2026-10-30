package com.alejandro.controlvehicles.integration.google;

/** One row of Google's autocomplete. */
public record PlaceSuggestion(String placeId, String mainText, String secondaryText) {

    public String label() {
        return secondaryText == null || secondaryText.isBlank() ? mainText : mainText + ", " + secondaryText;
    }

    @Override
    public String toString() {
        return label();
    }
}
