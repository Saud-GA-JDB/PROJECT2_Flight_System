package com.ga.saudsFlightSystem.model.request;

import lombok.Getter;
import lombok.Setter;

@Getter
public class AddAirlineAdminRequest {
    private String emailAddress;
    private String cpr;
    private String fName;
    private String lName;
    private String phoneNumberOpeningCode;
    private String phoneNumber;
    private String hireDate;
    private Long salary;
    private String securityQuestion;
    @Setter
    private String securityQuestionAnswer;

}
