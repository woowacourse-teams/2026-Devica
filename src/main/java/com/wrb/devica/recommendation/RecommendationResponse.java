package com.wrb.devica.recommendation;

import com.wrb.devica.product.SpecItemResponse;
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
                .map(value -> ItemResponse.of(
                    value,
                    recommended.itemReasons().getOrDefault(value.code(), List.of())))
                .toList());
        }
    }

    public record ItemResponse(
        String code,
        String displayName,
        String value,
        String displayValue,
        List<String> reasons) {

        private static ItemResponse of(SpecValue specValue, List<String> reasons) {
            SpecItemResponse item = SpecItemResponse.from(specValue);
            return new ItemResponse(item.code(), item.displayName(), item.value(), item.displayValue(), reasons);
        }
    }
}
