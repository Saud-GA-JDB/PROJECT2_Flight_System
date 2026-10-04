package com.ga.saudsFlightSystem.model.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.OptBoolean;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
public class AddAirlineAdminRequest {
    private String emailAddress;
    private String cpr;
    private String fName;
    private String lName;
    private String phoneNumberOpeningCode;
    private String phoneNumber;
    private String hireDate; //TODO: Handle in the service
    private Long salary;
    private String securityQuestion;
    @Setter
    private String securityQuestionAnswer;

}
