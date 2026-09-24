package com.example.minidi.domain;

import com.example.minidi.annotations.Component;

@Component
public final class CircularB {
    private final CircularA a;

    public CircularB(CircularA a) {
        this.a = a;
    }
}
