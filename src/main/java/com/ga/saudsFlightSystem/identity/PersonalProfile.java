package com.ga.saudsFlightSystem.identity;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PersonalProfile {
    private final String fName;
    private final String lName;
    private final String cpr;
    private final String phoneNumberOpeningCode;
    private final String phoneNumber;
}
