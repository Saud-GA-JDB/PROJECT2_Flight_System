package com.ga.saudsFlightSystem.model.request.response;

public record AccountSetupResponse(
        Long userId,
        String emailAddress,
        String role,
        String status,
        boolean active,
        String fName,
        String lName,
        String phoneNumberOpeningCode,
        String phoneNumber
) {}
