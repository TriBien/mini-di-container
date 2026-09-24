package com.example.minidi.experiments;

import com.example.minidi.domain.PaymentGateway;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class LoggingProxyExperiment {
    private LoggingProxyExperiment() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T wrap(T target, Class<T> contract) {
        InvocationHandler handler = (proxy, method, args) -> {
            System.out.println("[proxy before] " + method.getName());
            try {
                return method.invoke(target, args);
            } finally {
                System.out.println("[proxy after] " + method.getName());
            }
        };
        return (T) Proxy.newProxyInstance(
                contract.getClassLoader(),
                new Class<?>[]{contract},
                handler);
    }

    public static void run(PaymentGateway target) {
        PaymentGateway proxy = wrap(target, PaymentGateway.class);
        proxy.charge("proxy-demo", 1234);
    }
}
