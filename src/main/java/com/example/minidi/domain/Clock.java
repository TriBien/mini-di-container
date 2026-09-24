package com.example.minidi.domain;

import java.time.Instant;

public interface Clock {
    Instant now();
}
