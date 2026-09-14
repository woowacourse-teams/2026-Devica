package com.wrb.devica.recommendation;

import static com.wrb.devica.question.QuestionCode.AI_CODING_TOOL;
import static com.wrb.devica.question.QuestionCode.BUILD_WAIT;
import static com.wrb.devica.question.QuestionCode.CURRENT_MAC_CPU;
import static com.wrb.devica.question.QuestionCode.CURRENT_OS;
import static com.wrb.devica.question.QuestionCode.CURRENT_STORAGE;
import static com.wrb.devica.question.QuestionCode.CURRENT_WINDOWS_CPU;
import static com.wrb.devica.question.QuestionCode.DEV_ENVIRONMENT_SETUP;
import static com.wrb.devica.question.QuestionCode.IDE;
import static com.wrb.devica.question.QuestionCode.OVERHEATING;
import static com.wrb.devica.question.QuestionCode.PREFERRED_OS;
import static com.wrb.devica.question.QuestionCode.PROGRAMMING_LANGUAGE;
import static com.wrb.devica.question.QuestionCode.SLOWDOWN;
import static com.wrb.devica.question.QuestionCode.STORAGE_SHORTAGE;
import static com.wrb.devica.question.QuestionCode.USAGE_PERIOD;
import static org.assertj.core.api.Assertions.assertThat;

import com.wrb.devica.product.CpuTier;
import com.wrb.devica.product.LaptopSpec;
import com.wrb.devica.product.Os;
import com.wrb.devica.question.OptionCode;
import com.wrb.devica.question.QuestionCode;
import com.wrb.devica.question.option.AiCodingTool;
import com.wrb.devica.question.option.BuildWait;
import com.wrb.devica.question.option.CurrentMacCpu;
import com.wrb.devica.question.option.CurrentOs;
import com.wrb.devica.question.option.CurrentStorage;
import com.wrb.devica.question.option.CurrentWindowsCpu;
import com.wrb.devica.question.option.DevEnvironmentSetup;
import com.wrb.devica.question.option.Ide;
import com.wrb.devica.question.option.Overheating;
import com.wrb.devica.question.option.PreferredOs;
import com.wrb.devica.question.option.ProgrammingLanguage;
import com.wrb.devica.question.option.Slowdown;
import com.wrb.devica.question.option.StorageShortage;
import com.wrb.devica.question.option.UsagePeriod;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LaptopBackendAlgorithmTest {

    private final LaptopBackendAlgorithm algorithm = new LaptopBackendAlgorithm();

    @Test
    void 답변이_없으면_Mac_과_Windows_기본안을_함께_낸다() {
        // when
        List<RecommendedSpec> recommended = algorithm.recommend(Answers.empty());

        // then
        assertThat(recommended).extracting(LaptopBackendAlgorithmTest::spec)
            .containsExactly(
                new LaptopSpec(Os.MAC, CpuTier.BASIC, 24, 512),
                new LaptopSpec(Os.WINDOWS, CpuTier.P_HS, 24, 512));
    }

    @Test
    void 선호_OS_를_고르면_그_권장안만_낸다() {
        // when
        List<RecommendedSpec> recommended = algorithm.recommend(
            answers().with(PREFERRED_OS, PreferredOs.WINDOWS).build());

        // then
        assertThat(recommended).extracting(recommendedSpec -> spec(recommendedSpec).os())
            .containsExactly(Os.WINDOWS);
    }

    @Test
    void Java_계열_언어는_CPU_를_한_단계_올린다() {
        // when
        LaptopSpec windows = windowsSpec(answers()
            .with(PROGRAMMING_LANGUAGE, ProgrammingLanguage.JAVA_FAMILY));

        // then
        assertThat(windows.cpuTier()).isEqualTo(CpuTier.H);
    }

    @Test
    void 빌드_대기와_발열_경험은_CPU_를_각각_한_단계씩_올린다() {
        // when
        LaptopSpec windows = windowsSpec(answers()
            .with(BUILD_WAIT, BuildWait.OFTEN)
            .with(OVERHEATING, Overheating.OFTEN));

        // then
        assertThat(windows.cpuTier()).isEqualTo(CpuTier.HX);
    }

    @Test
    void Mac_권장_CPU_는_M_Pro_칩을_넘지_않는다() {
        // when
        LaptopSpec mac = macSpec(answers()
            .with(PROGRAMMING_LANGUAGE, ProgrammingLanguage.JAVA_FAMILY)
            .with(BUILD_WAIT, BuildWait.OFTEN)
            .with(OVERHEATING, Overheating.OFTEN));

        // then
        assertThat(mac.cpuTier()).isEqualTo(CpuTier.PRO);
    }

    @Test
    void 쓰던_노트북의_CPU_가_권장안보다_높으면_그_위로_올린다() {
        // given - 기본 등급은 P_HS 이지만 이미 H 를 쓰면서 발열을 겪었다
        LaptopSpec experienced = windowsSpec(answers()
            .with(CURRENT_OS, CurrentOs.WINDOWS)
            .with(CURRENT_WINDOWS_CPU, CurrentWindowsCpu.H)
            .with(OVERHEATING, Overheating.OFTEN));
        LaptopSpec unknown = windowsSpec(answers().with(OVERHEATING, Overheating.OFTEN));

        // then
        assertThat(experienced.cpuTier()).isEqualTo(CpuTier.HX);
        assertThat(unknown.cpuTier()).isEqualTo(CpuTier.H);
    }

    @Test
    void 쓰던_노트북의_OS_가_다르면_그_CPU_를_보지_않는다() {
        // given - Mac 을 쓰던 사람에게 Windows 권장안을 낼 때
        LaptopSpec windows = windowsSpec(answers()
            .with(CURRENT_OS, CurrentOs.MACOS)
            .with(CURRENT_MAC_CPU, CurrentMacCpu.MAX)
            .with(OVERHEATING, Overheating.OFTEN));

        // then
        assertThat(windows.cpuTier()).isEqualTo(CpuTier.H);
    }

    @Test
    void 개발_도구_부하가_크면_메모리를_올린다() {
        // when
        LaptopSpec windows = windowsSpec(answers().with(IDE, Ide.JETBRAINS));
        LaptopSpec mac = macSpec(answers().with(IDE, Ide.JETBRAINS));

        // then
        assertThat(windows.memoryGb()).isEqualTo(40);
        assertThat(mac.memoryGb()).isEqualTo(48);
    }

    @Test
    void 가끔_느려진_경험은_메모리를_8GB_만_올린다() {
        // when
        LaptopSpec windows = windowsSpec(answers().with(SLOWDOWN, Slowdown.SOMETIMES));

        // then
        assertThat(windows.memoryGb()).isEqualTo(32);
    }

    @Test
    void 원격_개발과_짧은_사용_계획은_메모리를_낮춘다() {
        // when
        LaptopSpec windows = windowsSpec(answers()
            .with(DEV_ENVIRONMENT_SETUP, DevEnvironmentSetup.REMOTE)
            .with(USAGE_PERIOD, UsagePeriod.TWO_YEARS));

        // then
        assertThat(windows.memoryGb()).isEqualTo(16);
    }

    @Test
    void 상향과_하향_조건이_함께_있으면_기준값을_유지한다() {
        // when
        LaptopSpec windows = windowsSpec(answers()
            .with(AI_CODING_TOOL, AiCodingTool.AI_EDITOR)
            .with(DEV_ENVIRONMENT_SETUP, DevEnvironmentSetup.REMOTE)
            .with(USAGE_PERIOD, UsagePeriod.TWO_YEARS));

        // then
        assertThat(windows.memoryGb()).isEqualTo(24);
    }

    @Test
    void 도커를_쓰면_저장_공간을_1TB_로_올린다() {
        // when
        LaptopSpec windows = windowsSpec(answers()
            .with(DEV_ENVIRONMENT_SETUP, DevEnvironmentSetup.DOCKER_FEW));

        // then
        assertThat(windows.storageGb()).isEqualTo(1024);
    }

    @Test
    void 하향_조건이_둘_이상이면_저장_공간을_낮춘다() {
        // when
        LaptopSpec windows = windowsSpec(answers()
            .with(CURRENT_STORAGE, CurrentStorage.TB_1_OR_MORE)
            .with(STORAGE_SHORTAGE, StorageShortage.NEVER)
            .with(USAGE_PERIOD, UsagePeriod.TWO_YEARS));

        // then
        assertThat(windows.storageGb()).isEqualTo(256);
    }

    @Test
    void 현재_SSD_가_작으면_용량_부족_경험을_상향_근거로_쓰지_않는다() {
        // when
        LaptopSpec windows = windowsSpec(answers()
            .with(CURRENT_STORAGE, CurrentStorage.GB_256_OR_LESS)
            .with(STORAGE_SHORTAGE, StorageShortage.OFTEN));

        // then
        assertThat(windows.storageGb()).isEqualTo(512);
    }

    @Test
    void Mac_은_계산된_메모리를_지원하는_CPU_등급으로_맞춘다() {
        // when - 48GB 는 M Pro 칩 조합에만 있다
        RecommendedSpec mac = macRecommendation(answers().with(IDE, Ide.JETBRAINS));

        // then
        assertThat(spec(mac).cpuTier()).isEqualTo(CpuTier.PRO);
        assertThat(mac.itemReasons().get("CPU_TIER"))
            .anyMatch(reason -> reason.contains("48GB RAM 지원 조합"));
    }

    @Test
    void 조정한_항목마다_기준값_근거_뒤에_조정_근거를_덧붙인다() {
        // when
        RecommendedSpec windows = windowsRecommendation(answers()
            .with(IDE, Ide.JETBRAINS)
            .with(USAGE_PERIOD, UsagePeriod.FIVE_PLUS_YEARS));

        // then
        assertThat(windows.itemReasons().get("MEMORY")).containsExactly(
            "Windows 기본 권장 메모리에서 시작했습니다.",
            "개발 도구와 작업 부하를 고려해 메모리를 16GB 높였습니다.",
            "오래 사용할 계획이 상향 판단을 보강했습니다.");
    }

    private LaptopSpec macSpec(AnswersBuilder builder) {
        return spec(macRecommendation(builder));
    }

    private LaptopSpec windowsSpec(AnswersBuilder builder) {
        return spec(windowsRecommendation(builder));
    }

    private RecommendedSpec macRecommendation(AnswersBuilder builder) {
        return algorithm.recommend(builder.with(PREFERRED_OS, PreferredOs.MACOS).build()).getFirst();
    }

    private RecommendedSpec windowsRecommendation(AnswersBuilder builder) {
        return algorithm.recommend(builder.with(PREFERRED_OS, PreferredOs.WINDOWS).build()).getFirst();
    }

    private static LaptopSpec spec(RecommendedSpec recommended) {
        return (LaptopSpec) recommended.spec();
    }

    private static AnswersBuilder answers() {
        return new AnswersBuilder();
    }

    private static class AnswersBuilder {

        private final Map<QuestionCode, List<OptionCode>> selected = new HashMap<>();

        private AnswersBuilder with(QuestionCode question, OptionCode... options) {
            selected.put(question, List.of(options));
            return this;
        }

        private Answers build() {
            return new Answers(selected);
        }
    }
}
