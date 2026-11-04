package com.wrb.devica.recommendation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.wrb.devica.product.domain.Os;
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
            new Rule("MEMORY", 2, often, "자주 느려집니다.", null),
            new Rule("STORAGE", 1, never, "느려진 적이 없습니다.", null),
            new Rule("MEMORY", 1, often, "느려짐이 잦아 한 단계 더 올립니다.", null),
            new Rule("MEMORY", -1, often, "짧게 쓸 계획입니다.", null));

        // when
        Requirements requirements = Judgement.judge(rules, List.of(), response, Os.MAC);

        // then
        assertThat(requirements.valueOf("MEMORY")).isEqualTo(2);
        assertThat(requirements.reasonsOf("MEMORY"))
            .containsExactly("자주 느려집니다.", "느려짐이 잦아 한 단계 더 올립니다.", "짧게 쓸 계획입니다.");
        assertThat(requirements.valueOf("STORAGE")).isZero();
        assertThat(requirements.reasonsOf("STORAGE")).isEmpty();
    }

    @Test
    void OS_를_지정한_규칙과_안내는_그_OS_권장안에만_적용하고_지정하지_않은_것은_모든_권장안에_적용한다() {
        // given
        SurveyResponse response = new SurveyResponse(Map.of("TARGET_PLATFORM", List.of("IOS")));
        Selected ios = new Selected("TARGET_PLATFORM", List.of("IOS"));
        List<Rule> rules = List.of(
            new Rule("STORAGE", 2, ios, "Xcode 가 자리를 차지합니다.", Os.MAC),
            new Rule("STORAGE", 1, ios, "모든 권장안의 근거입니다.", null));
        List<Note> notes = List.of(
            new Note(Os.WINDOWS, ios, "iOS 앱 빌드에는 Mac 이 필요합니다."),
            new Note(null, ios, "모든 권장안의 안내입니다."));

        // when
        Requirements mac = Judgement.judge(rules, notes, response, Os.MAC);
        Requirements windows = Judgement.judge(rules, notes, response, Os.WINDOWS);

        // then
        assertThat(mac.valueOf("STORAGE")).isEqualTo(3);
        assertThat(mac.reasonsOf("STORAGE")).containsExactly("Xcode 가 자리를 차지합니다.", "모든 권장안의 근거입니다.");
        assertThat(mac.notes()).containsExactly("모든 권장안의 안내입니다.");
        assertThat(windows.valueOf("STORAGE")).isEqualTo(1);
        assertThat(windows.reasonsOf("STORAGE")).containsExactly("모든 권장안의 근거입니다.");
        assertThat(windows.notes()).containsExactly("iOS 앱 빌드에는 Mac 이 필요합니다.", "모든 권장안의 안내입니다.");
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
