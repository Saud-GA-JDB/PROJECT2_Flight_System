package com.ga.identity.model.request.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProfileUpdateResponse {
    private String message;
    private String imageUrl;
}
