package com.wrb.devica.recommendation.domain;

import com.wrb.devica.product.domain.Spec;
import java.util.List;
import java.util.Map;

/**
 * 한 항목에 조정 조건이 여럿 걸리면 근거도 여럿이라, 화면이 줄 단위로 나눠 보여준다.
 */
public record RecommendedSpec(Spec spec, Map<String, List<String>> itemReasons) {
}
