package com.ga.saudsFlightSystem.model.request.response;

import com.ga.saudsFlightSystem.model.Currency;
import com.ga.saudsFlightSystem.model.Payment;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaymentResponse {
    private Long id;
    private Long amount;
    private Currency currency;
    private Payment.PaymentStatus status;
}
