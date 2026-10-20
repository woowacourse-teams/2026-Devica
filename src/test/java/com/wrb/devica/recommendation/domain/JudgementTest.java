package com.wrb.devica.recommendation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.wrb.devica.recommendation.domain.Condition.Selected;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JudgementTest {

    @Test
    void 조건이_맞는_규칙의_가중치를_항목별로_더하고_근거를_선언_순서대로_모은다() {
        // given
        SurveyResponse response = new SurveyResponse(Map.of("SLOWDOWN", List.of("OFTEN")));
        Selected often = new Selected("SLOWDOWN", List.of("OFTEN"));
        Selected never = new Selected("SLOWDOWN", List.of("NEVER"));
        List<Rule> rules = List.of(
            new Rule("MEMORY", 2, often, "자주 느려집니다."),
            new Rule("STORAGE", 1, never, "느려진 적이 없습니다."),
            new Rule("MEMORY", 1, often, "느려짐이 잦아 한 단계 더 올립니다."),
            new Rule("MEMORY", -1, often, "짧게 쓸 계획입니다."));

        // when
        Requirements requirements = Judgement.judge(rules, response);

        // then
        assertThat(requirements.valueOf("MEMORY")).isEqualTo(2);
        assertThat(requirements.reasonsOf("MEMORY"))
            .containsExactly("자주 느려집니다.", "느려짐이 잦아 한 단계 더 올립니다.", "짧게 쓸 계획입니다.");
        assertThat(requirements.valueOf("STORAGE")).isZero();
        assertThat(requirements.reasonsOf("STORAGE")).isEmpty();
    }

    @Test
    void 조건_연결은_같은_항목에서_먼저_맞은_값을_쓴다() {
        // given
        SurveyResponse response = new SurveyResponse(Map.of("CURRENT_MAC_CPU", List.of("BASIC")));
        Selected basic = new Selected("CURRENT_MAC_CPU", List.of("BASIC"));
        Selected macos = new Selected("PREFERRED_OS", List.of("MACOS"));
        List<ConditionMapping> mappings = List.of(
            new ConditionMapping("CURRENT_CPU_TIER", "BASIC", basic),
            new ConditionMapping("CURRENT_CPU_TIER", "PRO", basic),
            new ConditionMapping("PREFERRED_OS", "MAC", macos));

        // when
        Map<String, String> conditions = Judgement.map(mappings, response);

        // then
        assertThat(conditions).containsExactly(Map.entry("CURRENT_CPU_TIER", "BASIC"));
    }
}
