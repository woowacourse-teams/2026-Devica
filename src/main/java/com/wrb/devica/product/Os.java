package com.wrb.devica.product;

import lombok.Getter;

@Getter
public enum Os {

    MACOS("Mac"),
    WINDOWS("Windows");

    private final String displayName;

    Os(String displayName) {
        this.displayName = displayName;
    }
}
