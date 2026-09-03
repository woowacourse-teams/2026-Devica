package com.wrb.devica.recommendation;

import com.wrb.devica.product.Spec;
import java.util.Map;

public record RecommendedSpec(Spec spec, Map<String, String> itemReasons) {
}
