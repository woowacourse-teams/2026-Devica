package com.wrb.devica.recommendation.domain;

import java.util.List;

public record Recommendation(List<RecommendedSpec> specs, boolean answersOutdated) {
}
