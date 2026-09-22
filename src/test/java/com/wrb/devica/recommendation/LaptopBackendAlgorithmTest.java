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

    private static LaptopSpec spec(RecommendedSpec recommended) {
        return (LaptopSpec) recommended.spec();
    }

    private static AnswersBuilder answers() {
        return new AnswersBuilder();
    }

    @Test
    void 답변이_없으면_Mac_과_Windows_기본안을_함께_낸다() {
        // when
        List<RecommendedSpec> recommended = algorithm.recommend(Answers.empty());

        // then
        assertThat(recommended).extracting(LaptopBackendAlgorithmTest::spec)
            .containsExactly(
                new LaptopSpec(Os.MAC, CpuTier.BASIC, 16, 512),
                new LaptopSpec(Os.WINDOWS, CpuTier.P_HS, 16, 512));
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
    void 신호가_하나뿐이면_기본안에서_올리지_않는다() {
        // when
        LaptopSpec windows = windowsSpec(answers()
            .with(PROGRAMMING_LANGUAGE, ProgrammingLanguage.JAVA_FAMILY));

        // then
        assertThat(windows).isEqualTo(new LaptopSpec(Os.WINDOWS, CpuTier.P_HS, 16, 512));
    }

    @Test
    void 메모리_신호가_쌓일수록_단계적으로_올린다() {
        // when
        LaptopSpec twoSignals = windowsSpec(answers()
            .with(PROGRAMMING_LANGUAGE, ProgrammingLanguage.JAVA_FAMILY)
            .with(IDE, Ide.JETBRAINS));
        LaptopSpec sevenSignals = windowsSpec(answers()
            .with(PROGRAMMING_LANGUAGE, ProgrammingLanguage.JAVA_FAMILY)
            .with(IDE, Ide.MULTIPLE)
            .with(DEV_ENVIRONMENT_SETUP, DevEnvironmentSetup.DOCKER_MANY)
            .with(SLOWDOWN, Slowdown.OFTEN));

        // then
        assertThat(twoSignals.memoryGb()).isEqualTo(24);
        assertThat(sevenSignals.memoryGb()).isEqualTo(48);
    }

    @Test
    void 자주_느려진_경험을_가끔보다_무겁게_본다() {
        // when
        LaptopSpec sometimes = windowsSpec(answers().with(SLOWDOWN, Slowdown.SOMETIMES));
        LaptopSpec often = windowsSpec(answers().with(SLOWDOWN, Slowdown.OFTEN));

        // then
        assertThat(sometimes.memoryGb()).isEqualTo(16);
        assertThat(often.memoryGb()).isEqualTo(24);
    }

    @Test
    void 원격_개발과_짧은_사용_계획은_사양을_낮춘다() {
        // when
        LaptopSpec windows = windowsSpec(answers()
            .with(PROGRAMMING_LANGUAGE, ProgrammingLanguage.JAVA_FAMILY)
            .with(DEV_ENVIRONMENT_SETUP, DevEnvironmentSetup.REMOTE)
            .with(USAGE_PERIOD, UsagePeriod.TWO_YEARS));

        // then
        assertThat(windows).isEqualTo(new LaptopSpec(Os.WINDOWS, CpuTier.P_HS, 16, 256));
    }

    @Test
    void 도커를_여러_개_띄우면_저장_공간을_1TB_로_올린다() {
        // when
        LaptopSpec windows = windowsSpec(answers()
            .with(DEV_ENVIRONMENT_SETUP, DevEnvironmentSetup.DOCKER_MANY));

        // then
        assertThat(windows.storageGb()).isEqualTo(1024);
    }

    @Test
    void 도커를_한두_개_띄우는_것만으로는_저장_공간을_올리지_않는다() {
        // when
        LaptopSpec windows = windowsSpec(answers()
            .with(DEV_ENVIRONMENT_SETUP, DevEnvironmentSetup.DOCKER_FEW));

        // then
        assertThat(windows.storageGb()).isEqualTo(512);
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
    void 넉넉한_SSD_를_쓰면서_부족을_겪지_않았으면_저장_공간을_낮춘다() {
        // when
        LaptopSpec windows = windowsSpec(answers()
            .with(CURRENT_STORAGE, CurrentStorage.TB_1_OR_MORE)
            .with(STORAGE_SHORTAGE, StorageShortage.NEVER)
            .with(USAGE_PERIOD, UsagePeriod.TWO_YEARS));

        // then
        assertThat(windows.storageGb()).isEqualTo(256);
    }

    @Test
    void CPU_는_신호가_셋_이상_모여야_한_단계_오른다() {
        // when
        LaptopSpec oneSignal = windowsSpec(answers()
            .with(PROGRAMMING_LANGUAGE, ProgrammingLanguage.JAVA_FAMILY));
        LaptopSpec threeSignals = windowsSpec(answers()
            .with(PROGRAMMING_LANGUAGE, ProgrammingLanguage.JAVA_FAMILY)
            .with(BUILD_WAIT, BuildWait.OFTEN));

        // then
        assertThat(oneSignal.cpuTier()).isEqualTo(CpuTier.P_HS);
        assertThat(threeSignals.cpuTier()).isEqualTo(CpuTier.H);
    }

    @Test
    void CPU_신호가_다섯_이상이면_두_단계_오른다() {
        // when
        LaptopSpec windows = windowsSpec(answers()
            .with(PROGRAMMING_LANGUAGE, ProgrammingLanguage.JAVA_FAMILY)
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
    void 쓰던_CPU_에서_불편을_겪었으면_그_위를_권한다() {
        // when
        LaptopSpec experienced = windowsSpec(answers()
            .with(CURRENT_OS, CurrentOs.WINDOWS)
            .with(CURRENT_WINDOWS_CPU, CurrentWindowsCpu.H)
            .with(OVERHEATING, Overheating.OFTEN));
        LaptopSpec unknown = windowsSpec(answers().with(OVERHEATING, Overheating.OFTEN));

        // then
        assertThat(experienced.cpuTier()).isEqualTo(CpuTier.HX);
        assertThat(unknown.cpuTier()).isEqualTo(CpuTier.P_HS);
    }

    @Test
    void 쓰던_노트북의_OS_가_다르면_그_CPU_를_보지_않는다() {
        // when
        LaptopSpec windows = windowsSpec(answers()
            .with(CURRENT_OS, CurrentOs.MACOS)
            .with(CURRENT_MAC_CPU, CurrentMacCpu.MAX)
            .with(OVERHEATING, Overheating.OFTEN));
        LaptopSpec mac = macSpec(answers()
            .with(CURRENT_OS, CurrentOs.WINDOWS)
            .with(CURRENT_MAC_CPU, CurrentMacCpu.PRO)
            .with(OVERHEATING, Overheating.OFTEN));

        // then
        assertThat(windows.cpuTier()).isEqualTo(CpuTier.P_HS);
        assertThat(mac.cpuTier()).isEqualTo(CpuTier.BASIC);
    }

    @Test
    void Mac_은_메모리가_등급_상한을_넘으면_CPU_를_올린다() {
        // when - 48GB 는 M 칩 조합에 없다
        LaptopSpec mac = macSpec(answers()
            .with(IDE, Ide.MULTIPLE)
            .with(AI_CODING_TOOL, AiCodingTool.AI_EDITOR)
            .with(DEV_ENVIRONMENT_SETUP, DevEnvironmentSetup.LOCAL_MANY)
            .with(SLOWDOWN, Slowdown.OFTEN));

        // then
        assertThat(mac.cpuTier()).isEqualTo(CpuTier.PRO);
        assertThat(mac.memoryGb()).isEqualTo(48);
    }

    @Test
    void Mac_은_등급_최소_구성보다_적은_메모리를_올린다() {
        // when - M Pro 칩은 24GB 부터 시작한다
        LaptopSpec mac = macSpec(answers()
            .with(PROGRAMMING_LANGUAGE, ProgrammingLanguage.JAVA_FAMILY)
            .with(BUILD_WAIT, BuildWait.OFTEN)
            .with(OVERHEATING, Overheating.OFTEN));

        // then
        assertThat(mac.cpuTier()).isEqualTo(CpuTier.PRO);
        assertThat(mac.memoryGb()).isEqualTo(24);
    }

    @Test
    void 걸린_신호마다_근거를_남기고_마지막에_결과를_적는다() {
        // when
        RecommendedSpec windows = windowsRecommendation(answers()
            .with(PROGRAMMING_LANGUAGE, ProgrammingLanguage.JAVA_FAMILY)
            .with(IDE, Ide.JETBRAINS));

        // then
        assertThat(windows.itemReasons().get("MEMORY")).containsExactly(
            "Java·Kotlin·C# 계열은 빌드와 실행에 메모리를 더 씁니다.",
            "JetBrains IDE 는 인덱싱과 코드 분석에 메모리를 많이 씁니다.",
            "권장 메모리는 24GB 입니다.");
    }

    @Test
    void 쓰던_CPU_보다_높아지지_않으면_그_위를_권한다고_적지_않는다() {
        // when - Mac 은 M Pro 칩을 넘지 않아 올린 등급이 되내려온다
        RecommendedSpec fromPro = macRecommendation(answers()
            .with(CURRENT_OS, CurrentOs.MACOS)
            .with(CURRENT_MAC_CPU, CurrentMacCpu.PRO)
            .with(PROGRAMMING_LANGUAGE, ProgrammingLanguage.JAVA_FAMILY)
            .with(BUILD_WAIT, BuildWait.OFTEN));
        RecommendedSpec fromMax = macRecommendation(answers()
            .with(CURRENT_OS, CurrentOs.MACOS)
            .with(CURRENT_MAC_CPU, CurrentMacCpu.MAX)
            .with(PROGRAMMING_LANGUAGE, ProgrammingLanguage.JAVA_FAMILY)
            .with(BUILD_WAIT, BuildWait.OFTEN));

        // then
        assertThat(spec(fromPro).cpuTier()).isEqualTo(CpuTier.PRO);
        assertThat(spec(fromMax).cpuTier()).isEqualTo(CpuTier.PRO);
        assertThat(fromPro.itemReasons().get("REQUIRED_CPU"))
            .noneMatch(reason -> reason.contains("그보다 위를 권합니다"));
        assertThat(fromMax.itemReasons().get("REQUIRED_CPU"))
            .noneMatch(reason -> reason.contains("그보다 위를 권합니다"));
    }

    @Test
    void 결과는_살_수_있는_조합으로_맞춘_값으로_적는다() {
        // when - 48GB 때문에 CPU 가 오르고, M Pro 최소 구성 때문에 메모리가 오른다
        RecommendedSpec raisedCpu = macRecommendation(answers()
            .with(IDE, Ide.MULTIPLE)
            .with(AI_CODING_TOOL, AiCodingTool.AI_EDITOR)
            .with(DEV_ENVIRONMENT_SETUP, DevEnvironmentSetup.LOCAL_MANY)
            .with(SLOWDOWN, Slowdown.OFTEN));
        RecommendedSpec raisedMemory = macRecommendation(answers()
            .with(PROGRAMMING_LANGUAGE, ProgrammingLanguage.JAVA_FAMILY)
            .with(BUILD_WAIT, BuildWait.OFTEN)
            .with(OVERHEATING, Overheating.OFTEN));

        // then
        assertThat(raisedCpu.itemReasons().get("REQUIRED_CPU")).last()
            .isEqualTo("권장 CPU 는 M Pro 칩 입니다.");
        assertThat(raisedMemory.itemReasons().get("MEMORY")).last()
            .isEqualTo("권장 메모리는 24GB 입니다.");
    }

    private RecommendedSpec macRecommendation(AnswersBuilder builder) {
        return algorithm.recommend(builder.with(PREFERRED_OS, PreferredOs.MACOS).build()).getFirst();
    }

    private LaptopSpec macSpec(AnswersBuilder builder) {
        return spec(macRecommendation(builder));
    }

    private LaptopSpec windowsSpec(AnswersBuilder builder) {
        return spec(windowsRecommendation(builder));
    }

    private RecommendedSpec windowsRecommendation(AnswersBuilder builder) {
        return algorithm.recommend(builder.with(PREFERRED_OS, PreferredOs.WINDOWS).build()).getFirst();
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
