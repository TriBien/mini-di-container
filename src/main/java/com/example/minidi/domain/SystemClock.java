package com.example.minidi.domain;

import com.example.minidi.annotations.Component;

import java.time.Instant;

@Component
public final class SystemClock implements Clock {
    @Override
    public Instant now() {
        return Instant.now();
    }
}
