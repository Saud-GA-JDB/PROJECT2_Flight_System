package com.ga.saudsFlightSystem.model;

import lombok.*;

@Data
//@AllArgsConstructor
//@NoArgsConstructor
@RequiredArgsConstructor
public class Email {
    @NonNull
    private String recipient;
    @NonNull
    private String subject;
    @NonNull
    private String msgBody;
    private String attachment;
}
