package com.example.minidi.domain;

import com.example.minidi.annotations.Component;

@Component
public final class StripePaymentGateway implements PaymentGateway {
    @Override
    public void charge(String orderId, long cents) {
        System.out.printf("charge order=%s cents=%d%n", orderId, cents);
    }
}
