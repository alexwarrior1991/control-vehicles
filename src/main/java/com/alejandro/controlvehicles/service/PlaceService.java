package com.alejandro.controlvehicles.service;

import com.alejandro.controlvehicles.domain.Place;
import com.alejandro.controlvehicles.integration.google.PlaceSuggestion;
import com.alejandro.controlvehicles.integration.google.PlacesClient;
import com.alejandro.controlvehicles.persistence.PlaceRepository;

import java.util.List;
import java.util.Optional;

/** Origins and destinations: the stored ones first, Google's autocomplete for the rest. */
public final class PlaceService {

    private final PlaceRepository places;
    private final PlacesClient google;

    public PlaceService(PlaceRepository places, PlacesClient google) {
        this.places = places;
        this.google = google;
    }

    public List<Place> searchStored(String text) {
        return text == null || text.isBlank() ? List.of() : places.search(text, 8);
    }

    public List<PlaceSuggestion> searchGoogle(String text, String sessionToken) {
        return text == null || text.trim().length() < 3 ? List.of() : google.autocomplete(text.trim(), sessionToken);
    }

    /** The details of a Google suggestion, stored (once per Google place) so they are not asked again. */
    public Place resolve(PlaceSuggestion suggestion, String sessionToken) {
        return places.save(google.details(suggestion.placeId(), sessionToken));
    }

    public Optional<Place> findById(Long id) {
        return id == null ? Optional.empty() : places.findById(id);
    }

    public void markUsed(Place place) {
        if (place != null && place.id() != null) {
            places.markUsed(place.id());
        }
    }
}
