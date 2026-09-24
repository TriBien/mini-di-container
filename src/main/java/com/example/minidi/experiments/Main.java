package com.example.minidi.experiments;

import com.example.minidi.core.DiException;
import com.example.minidi.core.MiniApplicationContext;
import com.example.minidi.domain.Clock;
import com.example.minidi.domain.CircularA;
import com.example.minidi.domain.FieldInjectedService;
import com.example.minidi.domain.LifecycleProbe;
import com.example.minidi.domain.OrderService;
import com.example.minidi.domain.PaymentGateway;
import com.example.minidi.domain.PrototypeProbe;
import com.example.minidi.domain.StripePaymentGateway;
import com.example.minidi.domain.SystemClock;

public final class Main {
    public static void main(String[] args) {
        try (MiniApplicationContext context = new MiniApplicationContext()
                .bind(PaymentGateway.class, StripePaymentGateway.class)
                .bind(Clock.class, SystemClock.class)
                .register(OrderService.class)
                .register(FieldInjectedService.class)
                .register(LifecycleProbe.class)
                .register(PrototypeProbe.class)
                .register(CircularA.class)) {

            OrderService orders = context.getBean(OrderService.class);
            System.out.println("order placed at=" + orders.placeOrder("order-1", 4999));

            FieldInjectedService fieldInjected = context.getBean(FieldInjectedService.class);
            System.out.println("field injection ready=" + fieldInjected.isReady());

            LifecycleProbe lifecycle = context.getBean(LifecycleProbe.class);
            lifecycle.use();
            System.out.println("lifecycle=" + lifecycle.events());

            PrototypeProbe p1 = context.getBean(PrototypeProbe.class);
            PrototypeProbe p2 = context.getBean(PrototypeProbe.class);
            System.out.println("prototype same instance=" + (p1 == p2));

            LoggingProxyExperiment.run(context.getBean(PaymentGateway.class));

            try {
                context.getBean(CircularA.class);
            } catch (DiException e) {
                System.out.println("expected cycle failure=" + e.getMessage());
            }
        }
    }
}
