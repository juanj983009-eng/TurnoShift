package com.turnoshift.policy.infrastructure.web;

import java.util.List;

public record PolicyHistoryResponse(List<PolicyResponse> items) {
    public PolicyHistoryResponse {
        items = items != null ? List.copyOf(items) : List.of();
    }
}
