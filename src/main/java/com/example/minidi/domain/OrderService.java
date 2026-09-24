package com.example.minidi.domain;

import com.example.minidi.annotations.Component;
import com.example.minidi.annotations.Inject;

import java.time.Instant;

@Component
public final class OrderService {
    private final PaymentGateway paymentGateway;
    private final Clock clock;

    @Inject
    public OrderService(PaymentGateway paymentGateway, Clock clock) {
        this.paymentGateway = paymentGateway;
        this.clock = clock;
    }

    public Instant placeOrder(String orderId, long cents) {
        paymentGateway.charge(orderId, cents);
        return clock.now();
    }
}
