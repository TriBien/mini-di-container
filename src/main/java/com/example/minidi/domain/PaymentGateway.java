package com.example.minidi.domain;

public interface PaymentGateway {
    void charge(String orderId, long cents);
}
