package com.example.minidi.core;

import java.util.Objects;

public record BeanDefinition(
        Class<?> contract,
        Class<?> implementation,
        BeanScope scope
) {
    public BeanDefinition {
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(implementation, "implementation");
        Objects.requireNonNull(scope, "scope");
        if (!contract.isAssignableFrom(implementation)) {
            throw new IllegalArgumentException(
                    implementation.getName() + " does not implement/extend " + contract.getName());
        }
    }
}
