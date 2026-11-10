package com.ga.identity.model.request;

import lombok.Getter;

@Getter
public class VerifyEmailRequest {
    private String email;
    private String code;
}
