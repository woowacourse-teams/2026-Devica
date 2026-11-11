package com.wrb.devica.recommendation.domain;

import com.wrb.devica.product.domain.Os;
import java.util.List;

public record Basis(String item, Os os, String summary, List<String> points, List<String> upgrade, String tip) {

    public Basis {
        if (upgrade == null) {
            upgrade = List.of();
        }
    }
}
