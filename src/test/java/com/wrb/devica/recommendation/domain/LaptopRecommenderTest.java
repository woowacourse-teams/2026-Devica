package com.wrb.devica.recommendation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.wrb.devica.product.domain.ColorGamut;
import com.wrb.devica.product.domain.CpuTier;
import com.wrb.devica.product.domain.GpuTier;
import com.wrb.devica.product.domain.LaptopSpec;
import com.wrb.devica.product.domain.Os;
import com.wrb.devica.product.domain.SpecValue;
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
        Requirements two = new Requirements(Map.of("REQUIRED_CPU", 2), Map.of(), List.of());
        Requirements three = new Requirements(Map.of("REQUIRED_CPU", 3), Map.of(), List.of());

        // when
        List<RecommendedSpec> atTwo = recommender.recommend(INTERPRETATION, os -> two, Map.of());
        List<RecommendedSpec> atThree = recommender.recommend(INTERPRETATION, os -> three, Map.of());

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
        Requirements four = new Requirements(Map.of("REQUIRED_CPU", 4), Map.of(), List.of());
        Requirements five = new Requirements(Map.of("REQUIRED_CPU", 5), Map.of(), List.of());

        // when
        List<RecommendedSpec> atFour = recommender.recommend(INTERPRETATION, os -> four, Map.of());
        List<RecommendedSpec> atFive = recommender.recommend(INTERPRETATION, os -> five, Map.of());

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
        Requirements requirements = new Requirements(Map.of("MEMORY", 7), Map.of(), List.of());

        // when
        LaptopSpec mac = (LaptopSpec) recommender.recommend(INTERPRETATION, os -> requirements, Map.of()).get(0).spec();

        // then
        assertThat(mac.cpuTier()).isEqualTo(CpuTier.PRO);
        assertThat(mac.memoryGb()).isEqualTo(48);
    }

    @Test
    void Mac_은_CPU_등급에_없는_메모리를_가장_가까운_큰_용량으로_맞춘다() {
        // given
        LaptopRecommender recommender = new LaptopRecommender();
        Requirements requirements = new Requirements(Map.of("REQUIRED_CPU", 3, "MEMORY", 4), Map.of(), List.of());

        // when
        RecommendedSpec mac = recommender.recommend(INTERPRETATION, os -> requirements, Map.of()).get(0);
        RecommendedSpec windows = recommender.recommend(INTERPRETATION, os -> requirements, Map.of()).get(1);

        // then
        assertThat(((LaptopSpec) mac.spec()).memoryGb()).isEqualTo(48);
        assertThat(mac.itemReasons().get("MEMORY")).contains("M Pro 칩에서는 32GB 를 고를 수 없어 바로 위 용량인 48GB 를 권합니다.");
        assertThat(((LaptopSpec) windows.spec()).memoryGb()).isEqualTo(32);
    }

    @Test
    void 지금_쓰는_CPU_가_같은_OS_면_그보다_한_단계_위를_권한다() {
        // given
        LaptopRecommender recommender = new LaptopRecommender();
        Requirements requirements = new Requirements(Map.of(), Map.of(), List.of());
        Map<String, String> conditions = Map.of("CURRENT_CPU_TIER", "BASIC");

        // when
        List<RecommendedSpec> specs = recommender.recommend(INTERPRETATION, os -> requirements, conditions);

        // then
        assertThat(((LaptopSpec) specs.get(0).spec()).cpuTier()).isEqualTo(CpuTier.PRO);
        assertThat(((LaptopSpec) specs.get(1).spec()).cpuTier()).isEqualTo(CpuTier.P_HS);
    }

    @Test
    void 지금_쓰는_Mac_CPU_가_상한보다_높으면_낮춰_권하는_이유를_알린다() {
        // given
        LaptopRecommender recommender = new LaptopRecommender();
        Requirements requirements = new Requirements(Map.of(), Map.of(), List.of());
        Map<String, String> conditions = Map.of("CURRENT_CPU_TIER", "MAX");

        // when
        RecommendedSpec mac = recommender.recommend(INTERPRETATION, os -> requirements, conditions).get(0);

        // then
        assertThat(((LaptopSpec) mac.spec()).cpuTier()).isEqualTo(CpuTier.PRO);
        assertThat(mac.itemReasons().get("REQUIRED_CPU"))
            .contains("지금 쓰는 M Max 칩보다 낮지만, 이 작업에는 M Pro 칩이면 충분해 예산을 고려해 제한했습니다.")
            .doesNotContain("예산을 고려해 Mac 권장 CPU 는 M Pro 칩으로 제한했습니다.");
    }

    @Test
    void 지금_쓰는_Mac_CPU_가_상한과_같으면_같은_등급을_권하는_이유를_알린다() {
        // given
        LaptopRecommender recommender = new LaptopRecommender();
        Requirements requirements = new Requirements(Map.of(), Map.of(), List.of());
        Map<String, String> conditions = Map.of("CURRENT_CPU_TIER", "PRO");

        // when
        RecommendedSpec mac = recommender.recommend(INTERPRETATION, os -> requirements, conditions).get(0);

        // then
        assertThat(((LaptopSpec) mac.spec()).cpuTier()).isEqualTo(CpuTier.PRO);
        assertThat(mac.itemReasons().get("REQUIRED_CPU"))
            .contains("지금과 같은 M Pro 칩이라도 최신 세대를 고르면 성능이 오릅니다.");
    }

    @ParameterizedTest(name = "메모리 점수 {0} → {1}GB")
    @CsvSource({"1, 16", "2, 24", "3, 24", "4, 32", "6, 32", "7, 48"})
    void 메모리_점수가_구간_경계를_넘으면_한_단계_오른다(int score, int memoryGb) {
        // given
        LaptopRecommender recommender = new LaptopRecommender();
        Requirements requirements = new Requirements(Map.of("MEMORY", score), Map.of(), List.of());

        // when
        RecommendedSpec windows = recommender.recommend(INTERPRETATION, os -> requirements, Map.of()).get(1);

        // then
        assertThat(((LaptopSpec) windows.spec()).memoryGb()).isEqualTo(memoryGb);
    }

    @ParameterizedTest(name = "저장 공간 점수 {0} → {1}GB")
    @CsvSource({"-2, 256", "-1, 512", "2, 512", "3, 1024"})
    void 저장_공간_점수가_구간_경계를_넘으면_한_단계_오른다(int score, int storageGb) {
        // given
        LaptopRecommender recommender = new LaptopRecommender();
        Requirements requirements = new Requirements(Map.of("STORAGE", score), Map.of(), List.of());

        // when
        RecommendedSpec windows = recommender.recommend(INTERPRETATION, os -> requirements, Map.of()).get(1);

        // then
        assertThat(((LaptopSpec) windows.spec()).storageGb()).isEqualTo(storageGb);
    }

    @Test
    void 저장_공간_점수가_6에서_7이_되면_1TB_에서_2TB_로_오른다() {
        // given
        LaptopRecommender recommender = new LaptopRecommender();
        Requirements six = new Requirements(Map.of("STORAGE", 6), Map.of(), List.of());
        Requirements seven = new Requirements(Map.of("STORAGE", 7), Map.of(), List.of());

        // when
        RecommendedSpec atSix = recommender.recommend(INTERPRETATION, os -> six, Map.of()).get(0);
        RecommendedSpec atSeven = recommender.recommend(INTERPRETATION, os -> seven, Map.of()).get(0);

        // then
        assertThat(((LaptopSpec) atSix.spec()).storageGb()).isEqualTo(1024);
        assertThat(((LaptopSpec) atSeven.spec()).storageGb()).isEqualTo(2048);
        assertThat(atSeven.itemReasons().get("STORAGE")).containsExactly("권장 저장 공간은 2TB 입니다.");
    }

    @Test
    void 요구_항목의_근거_뒤에_결과_문장을_붙인다() {
        // given
        LaptopRecommender recommender = new LaptopRecommender();
        Requirements requirements = new Requirements(
            Map.of("MEMORY", 2), Map.of("MEMORY", List.of("자주 느려집니다.")), List.of());

        // when
        RecommendedSpec mac = recommender.recommend(INTERPRETATION, os -> requirements, Map.of()).get(0);

        // then
        assertThat(mac.itemReasons().get("MEMORY")).containsExactly("자주 느려집니다.", "권장 메모리는 24GB 입니다.");
    }

    @Test
    void OS_권장안마다_그_OS_의_판정_결과로_근거와_안내를_붙인다() {
        // given
        LaptopRecommender recommender = new LaptopRecommender();
        Requirements mac = new Requirements(Map.of(), Map.of("STORAGE", List.of("Mac 근거")), List.of());
        Requirements windows = new Requirements(Map.of(), Map.of(), List.of("Mac 이 필요합니다."));

        // when
        List<RecommendedSpec> specs = recommender.recommend(
            INTERPRETATION, os -> os == Os.MAC ? mac : windows, Map.of());

        // then
        assertThat(specs.get(0).itemReasons().get("STORAGE")).contains("Mac 근거");
        assertThat(specs.get(0).notes()).isEmpty();
        assertThat(specs.get(1).itemReasons().get("STORAGE")).doesNotContain("Mac 근거");
        assertThat(specs.get(1).notes()).containsExactly("Mac 이 필요합니다.");
    }

    @Test
    void 점수_해석_규칙에_화면과_GPU_구간이_없으면_권장_항목에_넣지_않는다() {
        // given
        LaptopRecommender recommender = new LaptopRecommender();
        Requirements requirements = new Requirements(Map.of("COLOR_GAMUT", 5, "GPU", 5), Map.of(), List.of());

        // when
        List<RecommendedSpec> specs = recommender.recommend(INTERPRETATION, os -> requirements, Map.of());

        // then
        assertThat(specs).allSatisfy(recommended -> assertThat(recommended.spec().values())
            .extracting(SpecValue::code)
            .containsExactly("OS", "REQUIRED_CPU", "MEMORY", "STORAGE"));
    }

    @Test
    void 화면과_GPU_는_점수_구간대로_권하고_Mac_도_같은_GPU_등급을_받는다() {
        // given
        LaptopRecommender recommender = new LaptopRecommender();
        LaptopInterpretation interpretation = withDisplayAndGpu(INTERPRETATION);
        Requirements requirements = new Requirements(Map.of("COLOR_GAMUT", 1, "GPU", 2), Map.of(), List.of());

        // when
        List<RecommendedSpec> specs = recommender.recommend(interpretation, os -> requirements, Map.of());

        // then
        LaptopSpec mac = (LaptopSpec) specs.get(0).spec();
        LaptopSpec windows = (LaptopSpec) specs.get(1).spec();
        assertThat(mac.colorGamut()).isEqualTo(ColorGamut.WIDE);
        assertThat(mac.gpuTier()).isEqualTo(GpuTier.MID);
        assertThat(windows.gpuTier()).isEqualTo(GpuTier.MID);
        assertThat(specs).allSatisfy(recommended -> assertThat(recommended.itemReasons().get("GPU"))
            .containsExactly("권장 GPU 는 " + GpuTier.MID.getDisplayName() + " 입니다."));
    }

    @Test
    void 화면이나_GPU_규칙에_점수_구간이_없으면_오류를_낸다() {
        // given
        Condition any = new Condition.Selected("Q", List.of("A"));
        List<Rule> rules = List.of(new Rule("GPU", 1, any, "근거", null), new Rule("MEMORY", 1, any, "근거", null));

        // when
        List<String> missing = LaptopRecommender.findRulesWithoutRange(INTERPRETATION, rules);
        List<String> present = LaptopRecommender.findRulesWithoutRange(withDisplayAndGpu(INTERPRETATION), rules);

        // then
        assertThat(missing).containsExactly("GPU 규칙이 있지만 점수 해석 규칙에 GPU 구간이 없습니다");
        assertThat(present).isEmpty();
    }

    private static LaptopInterpretation withDisplayAndGpu(LaptopInterpretation base) {
        return new LaptopInterpretation(base.cpu(), base.memory(), base.storage(), base.macCpuLimit(),
            List.of(new ScoreRange<>(0, ColorGamut.STANDARD), new ScoreRange<>(null, ColorGamut.WIDE)),
            List.of(new ScoreRange<>(1, GpuTier.ENTRY), new ScoreRange<>(3, GpuTier.MID),
                new ScoreRange<>(null, GpuTier.HIGH)));
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
            CpuTier.MAX, null, null);

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
            INTERPRETATION.cpu(), INTERPRETATION.memory(), INTERPRETATION.storage(), CpuTier.BASIC,
            null, null);

        // when
        List<String> errors = LaptopRecommender.findUnsupported(interpretation);

        // then
        assertThat(errors).containsExactly("Mac CPU 상한 BASIC 에서는 48GB 를 고를 수 없습니다. 최대 32GB 입니다");
    }
}
