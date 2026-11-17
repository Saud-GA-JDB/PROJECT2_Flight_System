package com.ga.common.contract.identity;

public record CustomerResponse(
        Long userId,
        String displayName,
        String emailAddress
) {}
