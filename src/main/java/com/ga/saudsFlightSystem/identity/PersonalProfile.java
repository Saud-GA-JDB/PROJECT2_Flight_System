package com.ga.saudsFlightSystem.identity;

import lombok.AllArgsConstructor;
import lombok.Getter;

@lombok.NoArgsConstructor
@lombok.Setter
@Getter
@AllArgsConstructor
public class PersonalProfile {
    private String fName;
    private String lName;
    private String cpr;
    private String phoneNumberOpeningCode;
    private String phoneNumber;
}
