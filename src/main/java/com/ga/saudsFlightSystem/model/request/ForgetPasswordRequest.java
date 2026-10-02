package com.ga.saudsFlightSystem.model.request;

import lombok.Getter;

@Getter
public class ForgetPasswordRequest {
    private String email;
    private String securityQuestionAnswer;
}
