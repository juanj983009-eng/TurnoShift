package com.turnoshift.unavailability.domain;

import com.turnoshift.unavailability.domain.exceptions.InvalidUnavailabilityDataException;

public record RestrictedNote(String value) {

    public static final int MAX_LENGTH = 500;

    public RestrictedNote {
        if (value == null) {
            throw new InvalidUnavailabilityDataException("La nota restringida no puede ser nula");
        }
        if (value.length() > MAX_LENGTH) {
            throw new InvalidUnavailabilityDataException("La nota restringida no puede superar los " + MAX_LENGTH + " caracteres");
        }
        if (value.isEmpty() || value.codePoints().allMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp))) {
            throw new InvalidUnavailabilityDataException("La nota restringida no puede estar vacía o contener solo espacios en blanco");
        }
    }

    public static RestrictedNote of(String value) {
        return new RestrictedNote(value);
    }
}
