package com.turnoshift.policy.domain;

public record PolicyThresholds(int minimumRestMinutes, int weeklyWarningMinutes) {

    public static final int MIN_REST_MIN = 1;
    public static final int MAX_REST_MIN = 1440; // 24 horas
    public static final int MIN_WEEKLY_WARNING_MIN = 1;
    public static final int MAX_WEEKLY_WARNING_MIN = 10080; // 7 días

    public PolicyThresholds {
        if (minimumRestMinutes < MIN_REST_MIN || minimumRestMinutes > MAX_REST_MIN) {
            throw new PolicyValidationException(
                    "VALIDATION_FAILED",
                    "El descanso mínimo debe estar entre " + MIN_REST_MIN + " y " + MAX_REST_MIN + " minutos"
            );
        }
        if (weeklyWarningMinutes < MIN_WEEKLY_WARNING_MIN || weeklyWarningMinutes > MAX_WEEKLY_WARNING_MIN) {
            throw new PolicyValidationException(
                    "VALIDATION_FAILED",
                    "El aviso semanal debe estar entre " + MIN_WEEKLY_WARNING_MIN + " y " + MAX_WEEKLY_WARNING_MIN + " minutos"
            );
        }
    }
}
