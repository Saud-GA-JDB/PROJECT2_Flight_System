package com.ga.identity.model.request;

import lombok.Getter;

@Getter
public class ForgetPasswordRequest {
    private String email;
    private String securityQuestionAnswer;
}
