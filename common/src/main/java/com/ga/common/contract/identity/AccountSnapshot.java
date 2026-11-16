package com.ga.common.contract.identity;

public record AccountSnapshot(
        Long userId,
        String role,
        String status,
        boolean active,
        String emailAddress
) {}
