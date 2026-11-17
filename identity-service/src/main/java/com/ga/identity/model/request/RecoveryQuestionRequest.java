package com.ga.identity.model.request;

import lombok.Getter;

@Getter
public class RecoveryQuestionRequest {
    private String email;
    private String code;
}
