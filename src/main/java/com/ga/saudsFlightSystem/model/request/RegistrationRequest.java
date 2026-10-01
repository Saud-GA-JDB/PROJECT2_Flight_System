package com.ga.saudsFlightSystem.model.request;

import lombok.Getter;

@Getter
public class RegistrationRequest {
    private String email;

    private String password;
    private String securityQuestion;
    private String securityQuestionAnswer;

    private String fName;
    private String lName;
    private String phoneNumber;
    private String phoneNumberOpeningCode;
}
