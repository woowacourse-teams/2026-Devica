package com.wrb.devica.recommendation.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrb.devica.purpose.domain.UsagePurposeCode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RecommendationDefinitionsTest {

    private static RecommendationDefinition read(String yaml) {
        return RecommendationDefinition.read(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void 검증을_통과하면_사용_목적으로_정의를_찾을_수_있다() {
        // given
        RecommendationDefinition definition = read("""
            survey:
              steps:
                - questions:
                    - { code: OS, options: [ { code: MAC } ] }
            rules:
              - { criterion: MEMORY, weight: 1, when: { question: OS, anyOf: [ MAC ] }, reason: 근거 }
            mappings: []
            """);

        // when
        RecommendationDefinitions definitions = new RecommendationDefinitions(
            Map.of(UsagePurposeCode.BACKEND_DEVELOPMENT, definition), Set.of("MEMORY"), Map.of());

        // then
        assertThat(definitions.get(UsagePurposeCode.BACKEND_DEVELOPMENT)).isEqualTo(definition);
    }

    @Test
    void 검증_오류가_있으면_사용_목적을_붙여_모두_담은_예외가_발생한다() {
        // given
        RecommendationDefinition definition = read("""
            survey:
              steps:
                - questions:
                    - { code: OS, options: [ { code: MAC } ] }
            rules:
              - { criterion: GPU, weight: 1, when: { question: OS, anyOf: [ MAC ] }, reason: 첫째 }
              - { criterion: MEMORY, weight: 1, when: { question: CPU, anyOf: [ PRO ] }, reason: 둘째 }
            mappings: []
            """);

        // when & then
        assertThatThrownBy(() -> new RecommendationDefinitions(
            Map.of(UsagePurposeCode.BACKEND_DEVELOPMENT, definition), Set.of("MEMORY"), Map.of()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("BACKEND_DEVELOPMENT: 규칙 '첫째': 없는 요구 항목입니다: GPU")
            .hasMessageContaining("BACKEND_DEVELOPMENT: 규칙 '둘째': 가리킬 수 없는 질문입니다: CPU");
    }
}
