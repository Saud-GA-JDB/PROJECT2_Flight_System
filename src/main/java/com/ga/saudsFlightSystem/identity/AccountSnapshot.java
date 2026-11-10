package com.ga.saudsFlightSystem.identity;

public record AccountSnapshot(
        Long userId,
        String role,
        String status,
        boolean active,
        String emailAddress
) {}
