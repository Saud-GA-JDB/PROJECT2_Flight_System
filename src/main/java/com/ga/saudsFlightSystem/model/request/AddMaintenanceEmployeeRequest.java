package com.ga.saudsFlightSystem.model.request;

import lombok.Getter;

@Getter
public class AddMaintenanceEmployeeRequest {
    private String fName;
    private String lName;
    private String cpr;
    private String emailAddress;
    private String phoneNumberOpeningCode;
    private String phoneNumber;
    private String licenseNumber;
    private String password;
    private String securityQuestion;
    private String securityQuestionAnswer;
}
