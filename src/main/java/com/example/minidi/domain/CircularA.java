package com.example.minidi.domain;

import com.example.minidi.annotations.Component;

@Component
public final class CircularA {
    private final CircularB b;

    public CircularA(CircularB b) {
        this.b = b;
    }
}
