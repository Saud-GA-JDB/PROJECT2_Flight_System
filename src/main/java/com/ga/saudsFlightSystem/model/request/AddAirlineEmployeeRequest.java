package com.ga.saudsFlightSystem.model.request;

import lombok.Getter;

@Getter
public class AddAirlineEmployeeRequest {
    private String fName;
    private String lName;
    private String emailAddress;
    private String cpr;
    private String phoneNumberOpeningCode;
    private String phoneNumber;
    private String hireDate;
    private Long salary;
    private String password;
    private String securityQuestion;
    private String securityQuestionAnswer;
}
