package com.ga.maintenance.model.request.response;

public record AccountSnapshot(
        Long userId,
        String role,
        String status,
        boolean active,
        String emailAddress
) {}
