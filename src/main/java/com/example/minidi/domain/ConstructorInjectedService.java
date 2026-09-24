package com.example.minidi.domain;

import com.example.minidi.annotations.Component;

@Component
public final class ConstructorInjectedService {
    private final Clock clock;

    public ConstructorInjectedService(Clock clock) {
        this.clock = clock;
    }

    public boolean hasClock() {
        return clock != null;
    }
}
