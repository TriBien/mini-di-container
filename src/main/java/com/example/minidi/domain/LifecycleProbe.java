package com.example.minidi.domain;

import com.example.minidi.annotations.Component;
import com.example.minidi.annotations.PostConstruct;
import com.example.minidi.annotations.PreDestroy;

import java.util.ArrayList;
import java.util.List;

@Component
public final class LifecycleProbe {
    private final List<String> events = new ArrayList<>();

    public LifecycleProbe() {
        events.add("construct");
    }

    @PostConstruct
    void postConstruct() {
        events.add("postConstruct");
    }

    public void use() {
        events.add("use");
    }

    @PreDestroy
    void preDestroy() {
        events.add("preDestroy");
    }

    public List<String> events() {
        return List.copyOf(events);
    }
}
