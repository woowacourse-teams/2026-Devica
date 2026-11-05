package com.wrb.devica.recommendation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RecommendationDefinitionTest {

    private static RecommendationDefinition read(String yaml) {
        return RecommendationDefinition.read(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void 모르는_키가_있으면_읽을_때_예외가_발생한다() {
        // given
        String yaml = """
            survey:
              steps:
                - questions:
                    - { code: OS, options: [ { code: MAC } ] }
            rules:
              - { criterion: MEMORY, weight: 1, when: { question: OS, anyOf: [ MAC ] }, reasn: 오타 }
            mappings: []
            """;

        // when & then
        assertThatThrownBy(() -> read(yaml))
            .hasMessageContaining("reasn");
    }

    @Test
    void 노출_조건은_단순형만_쓸_수_있다() {
        // given
        String yaml = """
            survey:
              steps:
                - questions:
                    - code: OS
                      options: [ { code: MAC } ]
                    - code: CPU
                      showWhen: { not: { question: OS, anyOf: [ MAC ] } }
                      options: [ { code: BASIC } ]
            rules: []
            mappings: []
            """;

        // when & then
        assertThatThrownBy(() -> read(yaml))
            .hasMessageContaining("not");
    }

    @Test
    void 설문_버전은_정의서_내용이_같으면_같고_다르면_달라진다() {
        // given
        String yaml = """
            survey:
              steps:
                - questions:
                    - { code: OS, options: [ { code: MAC } ] }
            rules: []
            mappings: []
            """;
        String changed = yaml.replace("MAC", "WINDOWS");

        // when
        String version = read(yaml).survey().version();
        String sameVersion = read(yaml).survey().version();
        String changedVersion = read(changed).survey().version();

        // then
        assertThat(version).hasSize(12).isEqualTo(sameVersion);
        assertThat(changedVersion).isNotEqualTo(version);
    }
}
