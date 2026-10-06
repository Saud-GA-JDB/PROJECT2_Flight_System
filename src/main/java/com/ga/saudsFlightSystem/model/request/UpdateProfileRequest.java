package com.ga.saudsFlightSystem.model.request;

import lombok.Getter;

@Getter
public class UpdateProfileRequest {
    private String phoneNumber;
    private String phoneNumberOpeningCode;
    private String securityQuestion;
    private String securityQuestionAnswer;

    // only FAA admin
    private String fName;
    private String lName;
    private String cpr;
    private String emailAddress;
    private Boolean active;
}
