package com.ga.saudsFlightSystem.model.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ChangePasswordRequest {
    private String newPassword;
}
