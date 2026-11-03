package com.wrb.devica.recommendation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.wrb.devica.product.domain.CpuTier;
import com.wrb.devica.product.domain.LaptopSpec;
import com.wrb.devica.product.domain.Os;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LaptopRecommenderTest {

    private static final LaptopInterpretation INTERPRETATION = RecommendationDefinition.read(
        LaptopRecommenderTest.class.getResourceAsStream("/recommendation/purposes/BACKEND_DEVELOPMENT.yml"))
        .interpretation();

    @Test
    void CPU_점수가_2에서_3이_되면_한_단계_오른다() {
        // given
        LaptopRecommender recommender = new LaptopRecommender();
        Requirements two = new Requirements(Map.of("REQUIRED_CPU", 2), Map.of());
        Requirements three = new Requirements(Map.of("REQUIRED_CPU", 3), Map.of());

        // when
        List<RecommendedSpec> atTwo = recommender.recommend(INTERPRETATION, two, Map.of());
        List<RecommendedSpec> atThree = recommender.recommend(INTERPRETATION, three, Map.of());

        // then
        assertThat(((LaptopSpec) atTwo.get(0).spec()).cpuTier()).isEqualTo(CpuTier.BASIC);
        assertThat(((LaptopSpec) atTwo.get(1).spec()).cpuTier()).isEqualTo(CpuTier.P_HS);
        assertThat(((LaptopSpec) atThree.get(0).spec()).cpuTier()).isEqualTo(CpuTier.PRO);
        assertThat(((LaptopSpec) atThree.get(1).spec()).cpuTier()).isEqualTo(CpuTier.H);
    }

    @Test
    void CPU_점수가_4에서_5가_되면_Windows_는_한_단계_오르고_Mac_은_M_Pro_칩으로_제한한다() {
        // given
        LaptopRecommender recommender = new LaptopRecommender();
        Requirements four = new Requirements(Map.of("REQUIRED_CPU", 4), Map.of());
        Requirements five = new Requirements(Map.of("REQUIRED_CPU", 5), Map.of());

        // when
        List<RecommendedSpec> atFour = recommender.recommend(INTERPRETATION, four, Map.of());
        List<RecommendedSpec> atFive = recommender.recommend(INTERPRETATION, five, Map.of());

        // then
        assertThat(((LaptopSpec) atFour.get(1).spec()).cpuTier()).isEqualTo(CpuTier.H);
        assertThat(((LaptopSpec) atFive.get(1).spec()).cpuTier()).isEqualTo(CpuTier.HX);
        assertThat(((LaptopSpec) atFour.get(0).spec()).cpuTier()).isEqualTo(CpuTier.PRO);
        assertThat(((LaptopSpec) atFive.get(0).spec()).cpuTier()).isEqualTo(CpuTier.PRO);
        assertThat(atFive.get(0).itemReasons().get("REQUIRED_CPU"))
            .contains("예산을 고려해 Mac 권장 CPU 는 M Pro 칩으로 제한했습니다.");
    }

    @Test
    void Mac_은_메모리가_CPU_등급의_상한을_넘으면_CPU_를_올린다() {
        // given
        LaptopRecommender recommender = new LaptopRecommender();
        Requirements requirements = new Requirements(Map.of("MEMORY", 7), Map.of());

        // when
        LaptopSpec mac = (LaptopSpec) recommender.recommend(INTERPRETATION, requirements, Map.of()).get(0).spec();

        // then
        assertThat(mac.cpuTier()).isEqualTo(CpuTier.PRO);
        assertThat(mac.memoryGb()).isEqualTo(48);
    }

    @Test
    void Mac_은_CPU_등급에_없는_메모리를_가장_가까운_큰_용량으로_맞춘다() {
        // given
        LaptopRecommender recommender = new LaptopRecommender();
        Requirements requirements = new Requirements(Map.of("REQUIRED_CPU", 3, "MEMORY", 4), Map.of());

        // when
        RecommendedSpec mac = recommender.recommend(INTERPRETATION, requirements, Map.of()).get(0);
        RecommendedSpec windows = recommender.recommend(INTERPRETATION, requirements, Map.of()).get(1);

        // then
        assertThat(((LaptopSpec) mac.spec()).memoryGb()).isEqualTo(48);
        assertThat(mac.itemReasons().get("MEMORY")).contains("M Pro 칩 에서 고를 수 있는 가장 가까운 용량은 48GB 입니다.");
        assertThat(((LaptopSpec) windows.spec()).memoryGb()).isEqualTo(32);
    }

    @Test
    void 지금_쓰는_CPU_가_같은_OS_면_그보다_한_단계_위를_권한다() {
        // given
        LaptopRecommender recommender = new LaptopRecommender();
        Requirements requirements = new Requirements(Map.of(), Map.of());
        Map<String, String> conditions = Map.of("CURRENT_CPU_TIER", "BASIC");

        // when
        List<RecommendedSpec> specs = recommender.recommend(INTERPRETATION, requirements, conditions);

        // then
        assertThat(((LaptopSpec) specs.get(0).spec()).cpuTier()).isEqualTo(CpuTier.PRO);
        assertThat(((LaptopSpec) specs.get(1).spec()).cpuTier()).isEqualTo(CpuTier.P_HS);
    }

    @ParameterizedTest(name = "메모리 점수 {0} → {1}GB")
    @CsvSource({"1, 16", "2, 24", "3, 24", "4, 32", "6, 32", "7, 48"})
    void 메모리_점수가_구간_경계를_넘으면_한_단계_오른다(int score, int memoryGb) {
        // given
        LaptopRecommender recommender = new LaptopRecommender();
        Requirements requirements = new Requirements(Map.of("MEMORY", score), Map.of());

        // when
        RecommendedSpec windows = recommender.recommend(INTERPRETATION, requirements, Map.of()).get(1);

        // then
        assertThat(((LaptopSpec) windows.spec()).memoryGb()).isEqualTo(memoryGb);
    }

    @ParameterizedTest(name = "저장 공간 점수 {0} → {1}GB")
    @CsvSource({"-2, 256", "-1, 512", "2, 512", "3, 1024"})
    void 저장_공간_점수가_구간_경계를_넘으면_한_단계_오른다(int score, int storageGb) {
        // given
        LaptopRecommender recommender = new LaptopRecommender();
        Requirements requirements = new Requirements(Map.of("STORAGE", score), Map.of());

        // when
        RecommendedSpec windows = recommender.recommend(INTERPRETATION, requirements, Map.of()).get(1);

        // then
        assertThat(((LaptopSpec) windows.spec()).storageGb()).isEqualTo(storageGb);
    }

    @Test
    void 저장_공간_점수가_6에서_7이_되면_1TB_에서_2TB_로_오른다() {
        // given
        LaptopRecommender recommender = new LaptopRecommender();
        Requirements six = new Requirements(Map.of("STORAGE", 6), Map.of());
        Requirements seven = new Requirements(Map.of("STORAGE", 7), Map.of());

        // when
        RecommendedSpec atSix = recommender.recommend(INTERPRETATION, six, Map.of()).get(0);
        RecommendedSpec atSeven = recommender.recommend(INTERPRETATION, seven, Map.of()).get(0);

        // then
        assertThat(((LaptopSpec) atSix.spec()).storageGb()).isEqualTo(1024);
        assertThat(((LaptopSpec) atSeven.spec()).storageGb()).isEqualTo(2048);
        assertThat(atSeven.itemReasons().get("STORAGE")).containsExactly("권장 저장 공간은 2TB 입니다.");
    }

    @Test
    void 요구_항목의_근거_뒤에_결과_문장을_붙인다() {
        // given
        LaptopRecommender recommender = new LaptopRecommender();
        Requirements requirements = new Requirements(Map.of("MEMORY", 2), Map.of("MEMORY", List.of("자주 느려집니다.")));

        // when
        RecommendedSpec mac = recommender.recommend(INTERPRETATION, requirements, Map.of()).get(0);

        // then
        assertThat(mac.itemReasons().get("MEMORY")).containsExactly("자주 느려집니다.", "권장 메모리는 24GB 입니다.");
    }

    @Test
    void 정의서의_점수_해석_규칙은_검증을_통과한다() {
        // when
        List<String> errors = LaptopRecommender.findUnsupported(INTERPRETATION);

        // then
        assertThat(errors).isEmpty();
    }

    @Test
    void 점수_해석_규칙이_노트북에서_낼_수_없는_값을_쓰면_오류를_낸다() {
        // given
        LaptopInterpretation interpretation = new LaptopInterpretation(
            Map.of(Os.MAC, List.of(new ScoreRange<>(2, CpuTier.BASIC), new ScoreRange<>(null, CpuTier.HX))),
            List.of(new ScoreRange<>(3, 16), new ScoreRange<>(1, 24), new ScoreRange<>(null, 20)),
            List.of(new ScoreRange<>(2, 512), new ScoreRange<>(6, 1024)),
            CpuTier.MAX);

        // when
        List<String> errors = LaptopRecommender.findUnsupported(interpretation);

        // then
        assertThat(errors).containsExactly(
            "CPU 구간 WINDOWS: 구간이 없습니다",
            "CPU 구간 MAC: MAC 의 CPU 등급이 아닙니다: HX",
            "메모리 구간: upTo 는 작은 것부터 적어야 합니다",
            "메모리 구간: [16, 24, 32, 48] 에 없는 값입니다: 20",
            "저장 공간 구간: 마지막 구간은 upTo 없이 나머지 점수를 모두 받아야 합니다",
            "Mac CPU 상한은 [BASIC, PRO] 중 하나여야 합니다: MAX");
    }

    @Test
    void Mac_CPU_상한에서_고를_수_없는_메모리가_구간에_있으면_오류를_낸다() {
        // given
        LaptopInterpretation interpretation = new LaptopInterpretation(
            INTERPRETATION.cpu(), INTERPRETATION.memory(), INTERPRETATION.storage(), CpuTier.BASIC);

        // when
        List<String> errors = LaptopRecommender.findUnsupported(interpretation);

        // then
        assertThat(errors).containsExactly("Mac CPU 상한 BASIC 에서는 48GB 를 고를 수 없습니다. 최대 32GB 입니다");
    }
}
