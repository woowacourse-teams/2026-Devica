package com.wrb.devica.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wrb.devica.product.domain.SpecValue;
import com.wrb.devica.product.dto.SpecItemResponse;
import com.wrb.devica.recommendation.domain.Basis;
import com.wrb.devica.recommendation.domain.Recommendation;
import com.wrb.devica.recommendation.domain.RecommendedSpec;
import java.util.List;

public record RecommendationResponse(
    List<SpecResponse> specs,
    @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean answersOutdated) {

    public static RecommendationResponse from(Recommendation recommendation) {
        return new RecommendationResponse(
            recommendation.specs().stream()
                .map(SpecResponse::from)
                .toList(),
            recommendation.answersOutdated());
    }

    public record SpecResponse(List<ItemResponse> items, boolean preferred, List<String> notes) {

        private static SpecResponse from(RecommendedSpec recommended) {
            return new SpecResponse(
                recommended.spec().values().stream()
                    .map(value -> ItemResponse.of(
                        value,
                        BasisResponse.from(recommended.itemBasis().get(value.code())),
                        recommended.itemReasons().getOrDefault(value.code(), List.of())))
                    .toList(),
                recommended.preferred(),
                recommended.notes());
        }
    }

    public record ItemResponse(
        String code,
        String displayName,
        String value,
        String displayValue,
        BasisResponse basis,
        List<String> reasons) {

        private static ItemResponse of(SpecValue specValue, BasisResponse basis, List<String> reasons) {
            SpecItemResponse item = SpecItemResponse.from(specValue);
            return new ItemResponse(item.code(), item.displayName(), item.value(), item.displayValue(), basis,
                reasons);
        }
    }

    public record BasisResponse(String summary, List<String> points, List<String> upgrade, String tip) {

        private static BasisResponse from(Basis basis) {
            if (basis == null) {
                return null;
            }

            return new BasisResponse(basis.summary(), basis.points(), basis.upgrade(), basis.tip());
        }
    }
}
