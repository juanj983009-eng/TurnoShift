package com.turnoshift.policy.domain;

public record PolicyReason(String value) {

    public static final int MAX_LENGTH = 500;

    public PolicyReason {
        if (value == null) {
            throw new PolicyValidationException("REASON_REQUIRED", "El motivo de activación es obligatorio");
        }
        if (value.length() > MAX_LENGTH) {
            throw new PolicyValidationException(
                    "REASON_REQUIRED",
                    "El motivo de activación no puede superar los " + MAX_LENGTH + " caracteres"
            );
        }
        boolean isAllWhitespace = true;
        int length = value.length();
        int offset = 0;
        while (offset < length) {
            int codePoint = value.codePointAt(offset);
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) {
                isAllWhitespace = false;
                break;
            }
            offset += Character.charCount(codePoint);
        }
        if (length == 0 || isAllWhitespace) {
            throw new PolicyValidationException(
                    "REASON_REQUIRED",
                    "El motivo de activación no puede estar vacío ni compuesto solo por espacios"
            );
        }
    }
}
