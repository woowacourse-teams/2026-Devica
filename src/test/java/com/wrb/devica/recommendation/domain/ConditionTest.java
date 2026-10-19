package com.wrb.devica.recommendation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.wrb.devica.recommendation.domain.Condition.All;
import com.wrb.devica.recommendation.domain.Condition.Any;
import com.wrb.devica.recommendation.domain.Condition.Not;
import com.wrb.devica.recommendation.domain.Condition.Selected;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConditionTest {

    @Test
    void 나열한_선택지_중_하나라도_골랐으면_참이다() {
        // given
        SurveyResponse response = new SurveyResponse(Map.of("OS", List.of("MAC")));

        // when & then
        assertThat(new Selected("OS", List.of("WINDOWS", "MAC")).evaluate(response)).isTrue();
        assertThat(new Selected("OS", List.of("WINDOWS")).evaluate(response)).isFalse();
        assertThat(new Selected("IDE", List.of("JETBRAINS")).evaluate(response)).isFalse();
    }

    @Test
    void All_은_모든_조건이_참일_때만_참이다() {
        // given
        SurveyResponse response = new SurveyResponse(Map.of("OS", List.of("MAC"), "IDE", List.of("JETBRAINS")));
        Condition mac = new Selected("OS", List.of("MAC"));
        Condition jetbrains = new Selected("IDE", List.of("JETBRAINS"));
        Condition windows = new Selected("OS", List.of("WINDOWS"));

        // when & then
        assertThat(new All(List.of(mac, jetbrains)).evaluate(response)).isTrue();
        assertThat(new All(List.of(mac, windows)).evaluate(response)).isFalse();
    }

    @Test
    void Any_는_조건_하나라도_참이면_참이다() {
        // given
        SurveyResponse response = new SurveyResponse(Map.of("OS", List.of("MAC")));
        Condition mac = new Selected("OS", List.of("MAC"));
        Condition windows = new Selected("OS", List.of("WINDOWS"));

        // when & then
        assertThat(new Any(List.of(windows, mac)).evaluate(response)).isTrue();
        assertThat(new Any(List.of(windows)).evaluate(response)).isFalse();
    }

    @Test
    void Not_은_조건의_참_거짓을_뒤집는다() {
        // given
        SurveyResponse response = new SurveyResponse(Map.of("OS", List.of("MAC")));

        // when & then
        assertThat(new Not(new Selected("OS", List.of("MAC"))).evaluate(response)).isFalse();
        assertThat(new Not(new Selected("OS", List.of("WINDOWS"))).evaluate(response)).isTrue();
    }

    @Test
    void 조건식_안의_모든_질문_참조를_꺼낸다() {
        // given
        Selected mac = new Selected("OS", List.of("MAC"));
        Selected jetbrains = new Selected("IDE", List.of("JETBRAINS"));
        Selected remote = new Selected("ENV", List.of("REMOTE"));
        Condition condition = new All(List.of(mac, new Any(List.of(jetbrains, new Not(remote)))));

        // when & then
        assertThat(condition.selections()).containsExactly(mac, jetbrains, remote);
    }
}
