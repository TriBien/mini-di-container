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

import java.util.List;

public final class SelfTest {
    private SelfTest() {}

    public static void main(String[] args) {
        testSingletonBinding();
        testConstructorInjection();
        testFieldInjection();
        testLifecycle();
        testPrototype();
        testCycle();
        testMissingBinding();
        System.out.println("ALL SELF-TESTS PASSED");
    }

    private static void testSingletonBinding() {
        try (MiniApplicationContext context = new MiniApplicationContext()
                .bind(PaymentGateway.class, StripePaymentGateway.class)) {
            assertSame(context.getBean(PaymentGateway.class), context.getBean(PaymentGateway.class));
            assertSame(context.getBean(PaymentGateway.class), context.getBean(StripePaymentGateway.class));
        }
    }

    private static void testConstructorInjection() {
        try (MiniApplicationContext context = baseContext()) {
            assertTrue(context.getBean(OrderService.class) != null);
        }
    }

    private static void testFieldInjection() {
        try (MiniApplicationContext context = new MiniApplicationContext()
                .bind(Clock.class, SystemClock.class)
                .register(FieldInjectedService.class)) {
            assertTrue(context.getBean(FieldInjectedService.class).isReady());
        }
    }

    private static void testLifecycle() {
        MiniApplicationContext context = new MiniApplicationContext().register(LifecycleProbe.class);
        LifecycleProbe probe = context.getBean(LifecycleProbe.class);
        probe.use();
        assertEquals(List.of("construct", "postConstruct", "use"), probe.events());
        context.close();
        assertEquals(List.of("construct", "postConstruct", "use", "preDestroy"), probe.events());
    }

    private static void testPrototype() {
        try (MiniApplicationContext context = new MiniApplicationContext().register(PrototypeProbe.class)) {
            assertNotSame(context.getBean(PrototypeProbe.class), context.getBean(PrototypeProbe.class));
        }
    }

    private static void testCycle() {
        try (MiniApplicationContext context = new MiniApplicationContext().register(CircularA.class)) {
            DiException e = assertThrows(() -> context.getBean(CircularA.class));
            assertTrue(e.getMessage().contains("CircularA -> CircularB -> CircularA"));
        }
    }

    private static void testMissingBinding() {
        try (MiniApplicationContext context = new MiniApplicationContext()) {
            DiException e = assertThrows(() -> context.getBean(PaymentGateway.class));
            assertTrue(e.getMessage().contains("No bean definition"));
        }
    }

    private static MiniApplicationContext baseContext() {
        return new MiniApplicationContext()
                .bind(PaymentGateway.class, StripePaymentGateway.class)
                .bind(Clock.class, SystemClock.class)
                .register(OrderService.class);
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("expected true");
    }

    private static void assertSame(Object a, Object b) {
        if (a != b) throw new AssertionError("expected same instance");
    }

    private static void assertNotSame(Object a, Object b) {
        if (a == b) throw new AssertionError("expected distinct instances");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected=" + expected + ", actual=" + actual);
    }

    private static DiException assertThrows(Runnable action) {
        try {
            action.run();
        } catch (DiException e) {
            return e;
        }
        throw new AssertionError("expected DiException");
    }
}
