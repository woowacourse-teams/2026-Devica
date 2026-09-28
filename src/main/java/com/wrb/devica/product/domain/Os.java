package com.wrb.devica.product.domain;

import lombok.Getter;

@Getter
public enum Os {

    WINDOWS("Windows"),
    MAC("Mac");

    private final String displayName;

    Os(String displayName) {
        this.displayName = displayName;
    }
}
