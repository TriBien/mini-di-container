package com.example.minidi.domain;

import com.example.minidi.annotations.Component;
import com.example.minidi.annotations.Inject;
import com.example.minidi.annotations.PostConstruct;

@Component
public final class FieldInjectedService {
    @Inject
    private Clock clock;

    private boolean ready;

    @PostConstruct
    void init() {
        ready = clock != null;
    }

    public boolean isReady() {
        return ready;
    }
}
