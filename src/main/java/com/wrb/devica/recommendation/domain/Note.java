package com.wrb.devica.recommendation.domain;

import com.wrb.devica.product.domain.Os;

public record Note(Os os, Condition when, String message) {

    boolean appliesTo(Os target) {
        return os == null || os == target;
    }
}
