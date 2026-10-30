package com.alejandro.controlvehicles.domain;

/** A person who drives the fleet's vehicles. */
public record Driver(Long id, String name, String licenceNumber, String phone, String email,
                     boolean active, String notes) {

    public Driver {
        name = Texts.required(name, "name");
        licenceNumber = Texts.trimToNull(licenceNumber);
        phone = Texts.trimToNull(phone);
        email = Texts.trimToNull(email);
        notes = Texts.trimToNull(notes);
    }

    public Driver withId(long newId) {
        return new Driver(newId, name, licenceNumber, phone, email, active, notes);
    }

    public Driver withActive(boolean value) {
        return new Driver(id, name, licenceNumber, phone, email, value, notes);
    }

    @Override
    public String toString() {
        return name;
    }
}
