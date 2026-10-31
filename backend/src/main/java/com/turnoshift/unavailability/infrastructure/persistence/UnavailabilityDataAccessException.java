package com.turnoshift.unavailability.infrastructure.persistence;

import org.springframework.dao.DataAccessException;

public class UnavailabilityDataAccessException extends RuntimeException {

    public UnavailabilityDataAccessException(String message, DataAccessException cause) {
        super(message, cause);
    }

    public UnavailabilityDataAccessException(DataAccessException cause) {
        super("Error de acceso a datos en indisponibilidades", cause);
    }
}
