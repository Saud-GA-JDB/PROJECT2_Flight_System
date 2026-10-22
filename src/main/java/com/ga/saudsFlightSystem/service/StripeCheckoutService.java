package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.model.request.response.PaymentResponse;
import com.ga.saudsFlightSystem.model.request.response.CheckoutResponse;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionRetrieveParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class StripeCheckoutService {
    @Autowired
    private StripeClient stripeClient;

    @Autowired
    private PaymentService paymentService;

    @Value("${stripe.success-url}")
    private String successUrl;

    @Value("${stripe.cancel-url}")
    private String cancelUrl;

    public PaymentResponse refreshPayment(Long bookingId) throws StripeException {
        PaymentResponse payment = paymentService.getPayment(bookingId);
        String sessionId = paymentService.getCheckoutSessionId(payment.getId());

        if (sessionId == null)
            throw new InvalidInformationException("Checkout has not been saved. Please retry the checkout request");

        Session session = stripeClient.v1().checkout().sessions().retrieve(
                sessionId,
                SessionRetrieveParams.builder().build());

        paymentService.updatePaymentFromStripe(session);

        return paymentService.getPayment(bookingId);
    }

    public CheckoutResponse startCheckout(Long bookingId) throws StripeException {
        PaymentResponse payment = paymentService.preparePayment(bookingId);
        String sessionId = paymentService.getCheckoutSessionId(payment.getId());

        Session session;

        if (sessionId == null) {
            session = createCheckoutSession(payment);
        } else {
            session = stripeClient.v1().checkout().sessions().retrieve(
                    sessionId,
                    SessionRetrieveParams.builder().build());
        }

        paymentService.updatePaymentFromStripe(session);
        return paymentService.saveCheckoutSession(bookingId, session);
    }

    public Session createCheckoutSession(PaymentResponse payment)
            throws StripeException {
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .addAllowedPaymentMethodType(
                        SessionCreateParams.AllowedPaymentMethodType.CARD)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .setClientReferenceId(payment.getId().toString())
                .putMetadata("paymentId", payment.getId().toString())
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency(payment.getCurrency()
                                                        .name().toLowerCase(Locale.ROOT))
                                                .setUnitAmount(payment.getAmount())
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData
                                                                .builder()
                                                                .setName("Flight booking")
                                                                .build())
                                                .build())
                                .build())
                .build();

        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey("payment-checkout-" + payment.getId())
                .build();

        return stripeClient.v1().checkout().sessions().create(params, options);
    }
}
