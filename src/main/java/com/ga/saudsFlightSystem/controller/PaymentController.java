package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.model.request.response.CheckoutResponse;
import com.ga.saudsFlightSystem.model.request.response.PaymentResponse;
import com.ga.saudsFlightSystem.service.StripeCheckoutService;
import com.stripe.exception.StripeException;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/payments")
public class PaymentController {
    private StripeCheckoutService stripeCheckoutService;

    @PostMapping("/bookings/{bookingId}/checkout")
    public CheckoutResponse startCheckout(@PathVariable Long bookingId)
            throws StripeException {
        return stripeCheckoutService.startCheckout(bookingId);
    }

    @GetMapping("/bookings/{bookingId}")
    public PaymentResponse getPayment(@PathVariable Long bookingId)
            throws StripeException {
        return stripeCheckoutService.refreshPayment(bookingId);
    }

    @GetMapping("/success")
    public String paymentSuccess() {
        return "Checkout completed. Request your booking's payment status to verify the result.";
    }

    @GetMapping("/cancel")
    public String paymentCancel() {
        return "Checkout cancelled. Payment has not been confirmed.";
    }
}
