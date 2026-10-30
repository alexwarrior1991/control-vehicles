package com.alejandro.controlvehicles.domain;

/**
 * An origin or destination, as Google Places describes it. Stored once and reused, so a frequent place
 * costs no further autocomplete call.
 */
public record Place(Long id, String googlePlaceId, String name, String address, Double latitude,
                    Double longitude, Country country) {

    public Place {
        name = Texts.required(name, "place name");
        googlePlaceId = Texts.trimToNull(googlePlaceId);
        address = Texts.trimToNull(address);
    }

    public Place withId(long newId) {
        return new Place(newId, googlePlaceId, name, address, latitude, longitude, country);
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }

    /** "Name, address" without repeating the name when the address already starts with it. */
    public String label() {
        if (address == null || address.startsWith(name)) {
            return address == null ? name : address;
        }
        return name + ", " + address;
    }

    @Override
    public String toString() {
        return label();
    }
}
