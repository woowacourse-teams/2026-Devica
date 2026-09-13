package com.wrb.devica.recommendation;

import com.wrb.devica.product.Spec;
import java.util.List;
import java.util.Map;

/**
 * 권장 사양과 항목별 근거. 근거는 한 항목에 여러 줄 붙는다 —
 * 조정 조건이 여럿 걸리면 화면이 줄 단위로 나눠 보여준다.
 */
public record RecommendedSpec(Spec spec, Map<String, List<String>> itemReasons) {
}
