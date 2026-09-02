package com.wrb.devica.recommendation;

import com.wrb.devica.product.SpecValue;
import java.util.List;

public record RecommendationResponse(List<SpecResponse> specs) {

    public static RecommendationResponse from(List<RecommendedSpec> recommendedSpecs) {
        return new RecommendationResponse(recommendedSpecs.stream()
            .map(SpecResponse::from)
            .toList());
    }

    public record SpecResponse(List<ItemResponse> items) {

        private static SpecResponse from(RecommendedSpec recommended) {
            return new SpecResponse(recommended.spec().values().stream()
                .map(value -> ItemResponse.from(
                    value,
                    recommended.itemReasons().get(value.code())))
                .toList());
        }
    }

    public record ItemResponse(
        String code,
        String displayName,
        String value,
        String displayValue,
        String reason) {

        private static ItemResponse from(SpecValue specValue, String reason) {
            SpecItemView view = SpecItemView.valueOf(specValue.code());
            return new ItemResponse(
                specValue.code(),
                view.displayName(),
                specValue.value(),
                view.displayValue(specValue.value()),
                reason);
        }
    }
}
