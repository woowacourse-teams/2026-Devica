package com.wrb.devica.fixture;

import java.util.concurrent.atomic.AtomicLong;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProductCodeFixture {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    public static String next() {
        return "CODE-" + SEQUENCE.incrementAndGet();
    }
}
