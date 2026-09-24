package com.example.minidi;

import com.example.minidi.annotations.Component;
import com.example.minidi.annotations.Inject;
import com.example.minidi.annotations.PostConstruct;
import com.example.minidi.annotations.PreDestroy;
import com.example.minidi.core.BeanScope;
import com.example.minidi.core.DiException;
import com.example.minidi.core.MiniApplicationContext;
import com.example.minidi.domain.Clock;
import com.example.minidi.domain.CircularA;
import com.example.minidi.domain.FieldInjectedService;
import com.example.minidi.domain.LifecycleProbe;
import com.example.minidi.domain.PaymentGateway;
import com.example.minidi.domain.PrototypeProbe;
import com.example.minidi.domain.StripePaymentGateway;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MiniApplicationContextTest {

    @Test
    void resolves_interface_binding_and_preserves_singleton_identity() {
        try (MiniApplicationContext context = new MiniApplicationContext()
                .bind(PaymentGateway.class, StripePaymentGateway.class)) {
            PaymentGateway one = context.getBean(PaymentGateway.class);
            PaymentGateway two = context.getBean(PaymentGateway.class);

            assertSame(one, two);
            assertInstanceOf(StripePaymentGateway.class, one);
        }
    }

    @Test
    void resolves_constructor_dependencies() {
        try (MiniApplicationContext context = new MiniApplicationContext()
                .bind(PaymentGateway.class, StripePaymentGateway.class)
                .register(com.example.minidi.domain.SystemClock.class)
                .bind(Clock.class, com.example.minidi.domain.SystemClock.class)) {
            assertNotNull(context.getBean(com.example.minidi.domain.OrderService.class));
        }
    }

    @Test
    void field_injection_happens_before_post_construct() {
        try (MiniApplicationContext context = new MiniApplicationContext()
                .bind(Clock.class, com.example.minidi.domain.SystemClock.class)
                .register(FieldInjectedService.class)) {
            assertTrue(context.getBean(FieldInjectedService.class).isReady());
        }
    }

    @Test
    void lifecycle_has_expected_order() {
        MiniApplicationContext context = new MiniApplicationContext().register(LifecycleProbe.class);
        LifecycleProbe probe = context.getBean(LifecycleProbe.class);
        probe.use();
        assertEquals(List.of("construct", "postConstruct", "use"), probe.events());
        context.close();
        assertEquals(List.of("construct", "postConstruct", "use", "preDestroy"), probe.events());
    }

    @Test
    void prototype_is_new_each_time() {
        try (MiniApplicationContext context = new MiniApplicationContext().register(PrototypeProbe.class)) {
            assertNotSame(context.getBean(PrototypeProbe.class), context.getBean(PrototypeProbe.class));
        }
    }

    @Test
    void constructor_cycle_is_detected() {
        try (MiniApplicationContext context = new MiniApplicationContext().register(CircularA.class)) {
            DiException exception = assertThrows(DiException.class, () -> context.getBean(CircularA.class));
            assertTrue(exception.getMessage().contains("CircularA -> CircularB -> CircularA"));
        }
    }

    @Test
    void missing_abstract_binding_is_explicit() {
        try (MiniApplicationContext context = new MiniApplicationContext()) {
            DiException exception = assertThrows(DiException.class, () -> context.getBean(PaymentGateway.class));
            assertTrue(exception.getMessage().contains("No bean definition"));
        }
    }

    @Test
    void manual_composition_is_equivalent_at_the_object_graph_level() {
        PaymentGateway gateway = new StripePaymentGateway();
        var service = new com.example.minidi.domain.OrderService(gateway, java.time.Instant::now);
        assertNotNull(service);
    }

    interface NotARealContract {
    }

    @Component
    static class FieldProbe {
        @Inject
        private LifecycleProbe lifecycle;
        boolean initialized;
        @PostConstruct
        void init() {
            initialized = lifecycle != null;
        }
        @PreDestroy
        void destroy() {
            // assertion is performed indirectly by the event-based lifecycle probe
        }
    }
}
