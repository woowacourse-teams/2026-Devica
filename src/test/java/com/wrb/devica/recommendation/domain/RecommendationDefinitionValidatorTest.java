package com.wrb.devica.recommendation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RecommendationDefinitionValidatorTest {

    private static RecommendationDefinition read(String yaml) {
        return RecommendationDefinition.read(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void 참조가_모두_맞으면_오류가_없다() {
        // given
        RecommendationDefinitionValidator validator = new RecommendationDefinitionValidator(
            Set.of("MEMORY"), Map.of("PREFERRED_OS", Set.of("MAC", "WINDOWS")));
        RecommendationDefinition definition = read("""
            survey:
              steps:
                - questions:
                    - { code: OS, options: [ { code: MAC }, { code: WINDOWS } ] }
                    - code: CPU
                      showWhen: { question: OS, anyOf: [ MAC ] }
                      options: [ { code: BASIC }, { code: PRO } ]
            rules:
              - criterion: MEMORY
                weight: 1
                when: { all: [ { question: OS, anyOf: [ MAC ] }, { not: { question: CPU, anyOf: [ PRO ] } } ] }
                reason: 근거
            mappings:
              - { criterion: PREFERRED_OS, value: MAC, when: { question: OS, anyOf: [ MAC ] } }
            """);

        // when
        List<String> errors = validator.validate(definition);

        // then
        assertThat(errors).isEmpty();
    }

    @Test
    void 규칙이_없는_질문이나_선택지를_가리키면_오류를_낸다() {
        // given
        RecommendationDefinitionValidator validator = new RecommendationDefinitionValidator(Set.of("MEMORY"), Map.of());
        RecommendationDefinition definition = read("""
            survey:
              steps:
                - questions:
                    - { code: OS, options: [ { code: MAC }, { code: WINDOWS } ] }
            rules:
              - { criterion: MEMORY, weight: 1, when: { question: GPU, anyOf: [ RTX ] }, reason: 없는 질문 }
              - { criterion: MEMORY, weight: 1, when: { any: [ { question: OS, anyOf: [ LINUX ] } ] }, reason: 없는 선택지 }
            mappings: []
            """);

        // when
        List<String> errors = validator.validate(definition);

        // then
        assertThat(errors).containsExactly(
            "규칙 '없는 질문': 가리킬 수 없는 질문입니다: GPU",
            "규칙 '없는 선택지': OS 에 없는 선택지입니다: LINUX");
    }

    @Test
    void 없는_요구_항목이나_조건_항목_값을_쓰면_오류를_낸다() {
        // given
        RecommendationDefinitionValidator validator = new RecommendationDefinitionValidator(
            Set.of("MEMORY"), Map.of("PREFERRED_OS", Set.of("MAC", "WINDOWS")));
        RecommendationDefinition definition = read("""
            survey:
              steps:
                - questions:
                    - { code: OS, options: [ { code: MAC } ] }
            rules:
              - { criterion: GPU, weight: 1, when: { question: OS, anyOf: [ MAC ] }, reason: 근거 }
            mappings:
              - { criterion: PREFERRED_OS, value: LINUX, when: { question: OS, anyOf: [ MAC ] } }
              - { criterion: BUDGET, value: LOW, when: { question: OS, anyOf: [ MAC ] } }
            """);

        // when
        List<String> errors = validator.validate(definition);

        // then
        assertThat(errors).containsExactly(
            "규칙 '근거': 없는 요구 항목입니다: GPU",
            "조건 연결 PREFERRED_OS=LINUX: 항목이 가질 수 없는 값입니다",
            "조건 연결 BUDGET=LOW: 없는 조건 항목입니다");
    }

    @Test
    void 규칙이나_조건_연결에_조건이_없으면_오류를_낸다() {
        // given
        RecommendationDefinitionValidator validator = new RecommendationDefinitionValidator(
            Set.of("MEMORY"), Map.of("PREFERRED_OS", Set.of("MAC")));
        RecommendationDefinition definition = read("""
            survey:
              steps:
                - questions:
                    - { code: OS, options: [ { code: MAC } ] }
            rules:
              - { criterion: MEMORY, weight: 1, reason: 조건 없음 }
            mappings:
              - { criterion: PREFERRED_OS, value: MAC }
            """);

        // when
        List<String> errors = validator.validate(definition);

        // then
        assertThat(errors).containsExactly(
            "규칙 '조건 없음': 조건이 없습니다",
            "조건 연결 PREFERRED_OS=MAC: 조건이 없습니다");
    }

    @Test
    void 노출_조건이_뒤에_나오는_질문을_가리키면_오류를_낸다() {
        // given
        RecommendationDefinitionValidator validator = new RecommendationDefinitionValidator(Set.of(), Map.of());
        RecommendationDefinition definition = read("""
            survey:
              steps:
                - questions:
                    - { code: CPU, showWhen: { question: OS, anyOf: [ MAC ] }, options: [ { code: BASIC } ] }
                - questions:
                    - { code: OS, options: [ { code: MAC } ] }
            rules: []
            mappings: []
            """);

        // when
        List<String> errors = validator.validate(definition);

        // then
        assertThat(errors).containsExactly("질문 CPU 의 노출 조건: 가리킬 수 없는 질문입니다: OS");
    }

    @Test
    void 배타_선택지가_선택지에_없으면_오류를_낸다() {
        // given
        RecommendationDefinitionValidator validator = new RecommendationDefinitionValidator(Set.of(), Map.of());
        RecommendationDefinition definition = read("""
            survey:
              steps:
                - questions:
                    - { code: LANGUAGE, input: MULTI, exclusive: UNDECIDED, options: [ { code: JAVA } ] }
            rules: []
            mappings: []
            """);

        // when
        List<String> errors = validator.validate(definition);

        // then
        assertThat(errors).containsExactly("질문 LANGUAGE: 배타 선택지가 선택지에 없습니다: UNDECIDED");
    }

    @Test
    void 질문_코드가_겹치면_오류를_낸다() {
        // given
        RecommendationDefinitionValidator validator = new RecommendationDefinitionValidator(Set.of(), Map.of());
        RecommendationDefinition definition = read("""
            survey:
              steps:
                - questions:
                    - { code: OS, options: [ { code: MAC } ] }
                - questions:
                    - { code: OS, options: [ { code: WINDOWS } ] }
            rules: []
            mappings: []
            """);

        // when
        List<String> errors = validator.validate(definition);

        // then
        assertThat(errors).containsExactly("질문 코드가 겹칩니다: OS");
    }

    @Test
    void 한_질문_안에서_선택지_코드가_겹치면_오류를_낸다() {
        // given
        RecommendationDefinitionValidator validator = new RecommendationDefinitionValidator(Set.of(), Map.of());
        RecommendationDefinition definition = read("""
            survey:
              steps:
                - questions:
                    - { code: OS, options: [ { code: MAC }, { code: MAC } ] }
            rules: []
            mappings: []
            """);

        // when
        List<String> errors = validator.validate(definition);

        // then
        assertThat(errors).containsExactly("질문 OS: 선택지 코드가 겹칩니다: MAC");
    }

    @Test
    void 안내가_없는_질문이나_선택지를_가리키거나_조건이_없으면_오류를_낸다() {
        // given
        RecommendationDefinitionValidator validator = new RecommendationDefinitionValidator(Set.of(), Map.of());
        RecommendationDefinition definition = read("""
            survey:
              steps:
                - questions:
                    - { code: OS, options: [ { code: MAC } ] }
            rules: []
            mappings: []
            notes:
              - { os: WINDOWS, when: { question: GPU, anyOf: [ RTX ] }, message: 없는 질문 }
              - { os: WINDOWS, when: { question: OS, anyOf: [ LINUX ] }, message: 없는 선택지 }
              - { os: MAC, message: 조건 없음 }
            """);

        // when
        List<String> errors = validator.validate(definition);

        // then
        assertThat(errors).containsExactly(
            "안내 '없는 질문': 가리킬 수 없는 질문입니다: GPU",
            "안내 '없는 선택지': OS 에 없는 선택지입니다: LINUX",
            "안내 '조건 없음': 조건이 없습니다");
    }
    @Test
    void 지운_코드_줄이_지금_설문과_맞으면_오류가_없다() {
        // given
        RecommendationDefinitionValidator validator = new RecommendationDefinitionValidator(Set.of(), Map.of());
        RecommendationDefinition definition = read("""
            survey:
              steps:
                - questions:
                    - { code: HEAT_FELT, options: [ { code: OFTEN }, { code: NEVER } ] }
              retired:
                - { question: HEAT, to: HEAT_FELT, was: "뜨거워지나요?" }
                - { question: OLD_HEAT, to: HEAT, was: "발열을 겪나요?" }
                - { question: AI_TOOL, drop: true, was: "AI 도구를 쓰나요?" }
                - { question: HEAT_FELT, option: SOMETIMES, to: OFTEN, was: 가끔 }
                - { question: HEAT_FELT, option: UNKNOWN, drop: true, was: 모르겠다 }
                - { question: HEAT_FELT, dropInvalid: true }
            rules: []
            mappings: []
            """);

        // when
        List<String> errors = validator.validate(definition);

        // then
        assertThat(errors).isEmpty();
    }

    @Test
    void 지운_코드_줄이_지금_설문과_맞지_않으면_오류를_낸다() {
        // given
        RecommendationDefinitionValidator validator = new RecommendationDefinitionValidator(Set.of(), Map.of());
        RecommendationDefinition definition = read("""
            survey:
              steps:
                - questions:
                    - { code: HEAT, options: [ { code: OFTEN }, { code: NEVER } ] }
              retired:
                - { question: HEAT, drop: true }
                - { question: GONE, to: MISSING }
                - { question: LOOP_A, to: LOOP_B }
                - { question: LOOP_B, to: LOOP_A }
                - { question: BOTH, to: HEAT, drop: true }
                - { question: HEAT, option: OFTEN, drop: true }
                - { question: HEAT, option: RARELY, to: SOMETIMES }
                - { question: HEAT, option: RARELY, drop: true }
                - { question: NOT_NOW, option: X, drop: true }
                - { question: NOT_NOW, dropInvalid: true }
            rules: []
            mappings: []
            """);

        // when
        List<String> errors = validator.validate(definition);

        // then
        assertThat(errors).containsExactly(
            "지운 코드 HEAT: 지금 있는 질문입니다",
            "지운 코드 GONE: to 가 지금 질문으로 이어지지 않습니다: MISSING",
            "지운 코드 LOOP_A: to 가 지금 질문으로 이어지지 않습니다: LOOP_B",
            "지운 코드 LOOP_B: to 가 지금 질문으로 이어지지 않습니다: LOOP_A",
            "지운 코드 BOTH: to 와 drop 중 하나만 씁니다",
            "지운 코드 HEAT.OFTEN: 지금 있는 선택지입니다",
            "지운 코드 HEAT.RARELY: to 가 지금 선택지가 아닙니다: SOMETIMES",
            "지운 코드 HEAT.RARELY: 같은 코드의 줄이 겹칩니다",
            "지운 코드 NOT_NOW.X: 지금 질문이 아닙니다",
            "지운 코드 NOT_NOW: 지금 질문이 아닙니다");
    }
}
