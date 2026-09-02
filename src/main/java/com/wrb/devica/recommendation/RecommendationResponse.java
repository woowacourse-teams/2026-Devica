package com.wrb.devica.recommendation;

import com.wrb.devica.product.SpecItem;
import java.util.List;

public record RecommendationResponse(List<SpecResponse> specs) {

    public static RecommendationResponse from(List<RecommendedSpec> recommendedSpecs) {
        return new RecommendationResponse(recommendedSpecs.stream()
                .map(SpecResponse::from)
                .toList());
    }

    public record SpecResponse(List<ItemResponse> items) {

        private static SpecResponse from(RecommendedSpec recommended) {
            return new SpecResponse(recommended.spec().toItems().stream()
                    .map(item -> ItemResponse.of(item, recommended.itemReasons().get(item.code())))
                    .toList());
        }
    }

    public record ItemResponse(String code, String displayName, String value, String displayValue, String reason) {

        private static ItemResponse of(SpecItem item, String reason) {
            return new ItemResponse(
                    item.code(),
                    item.displayName(),
                    item.value(),
                    item.displayValue(),
                    reason);
        }
    }
}
