package com.wrb.devica.recommendation;

import com.wrb.devica.product.Spec;
import com.wrb.devica.product.SpecValue;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record RecommendedSpec(Spec spec, Map<String, String> itemReasons) {

    public RecommendedSpec {
        validateItemsAndReasonsMatch(spec, itemReasons);
        validateReasonsNotBlank(itemReasons);
    }

    private static void validateItemsAndReasonsMatch(Spec spec, Map<String, String> itemReasons) {
        Set<String> itemCodes = spec.values().stream()
            .map(SpecValue::code)
            .collect(Collectors.toSet());
        if (!itemCodes.equals(itemReasons.keySet())) {
            throw new IllegalArgumentException(
                "사양 항목과 근거가 일대일로 대응하지 않습니다: " + itemCodes + ", " + itemReasons.keySet());
        }
    }

    private static void validateReasonsNotBlank(Map<String, String> itemReasons) {
        if (itemReasons.values().stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("근거가 비어 있는 사양 항목이 있습니다: " + itemReasons);
        }
    }
}
