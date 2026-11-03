package com.wrb.devica.recommendation.domain;

import com.wrb.devica.product.domain.Os;

public record Rule(String criterion, int weight, Condition when, String reason, Os os) {

    boolean appliesTo(Os target) {
        return os == null || os == target;
    }
}
