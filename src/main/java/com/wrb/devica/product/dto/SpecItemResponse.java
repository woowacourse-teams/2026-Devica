package com.wrb.devica.product.dto;

import com.wrb.devica.product.domain.SpecValue;
import java.util.List;

public record SpecItemResponse(
    String code,
    String displayName,
    String value,
    String displayValue
) {
    public static List<SpecItemResponse> from(List<SpecValue> specValues) {
        return specValues.stream()
            .map(SpecItemResponse::from)
            .toList();
    }

    public static SpecItemResponse from(SpecValue specValue) {
        SpecItemView view = SpecItemView.valueOf(specValue.code());

        return new SpecItemResponse(
            specValue.code(),
            view.displayName(),
            specValue.value(),
            view.displayValue(specValue.value())
        );
    }
}
