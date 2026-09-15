package com.wrb.devica.recommendation;

import static com.wrb.devica.question.QuestionCode.AI_CODING_TOOL;
import static com.wrb.devica.question.QuestionCode.BUILD_WAIT;
import static com.wrb.devica.question.QuestionCode.CURRENT_MAC_CPU;
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

import com.wrb.devica.product.CpuTier;
import com.wrb.devica.product.LaptopSpec;
import com.wrb.devica.product.Os;
import com.wrb.devica.purpose.UsagePurposeCode;
import com.wrb.devica.question.OptionCode;
import com.wrb.devica.question.QuestionCode;
import com.wrb.devica.question.option.AiCodingTool;
import com.wrb.devica.question.option.BuildWait;
import com.wrb.devica.question.option.CurrentStorage;
import com.wrb.devica.question.option.DevEnvironmentSetup;
import com.wrb.devica.question.option.Ide;
import com.wrb.devica.question.option.Overheating;
import com.wrb.devica.question.option.PreferredOs;
import com.wrb.devica.question.option.ProgrammingLanguage;
import com.wrb.devica.question.option.Slowdown;
import com.wrb.devica.question.option.StorageShortage;
import com.wrb.devica.question.option.UsagePeriod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 백엔드 개발 노트북 추천. 항목마다 신호를 모아 그 합으로 단계를 올리거나 내린다.
 * <p>
 * 조건 하나로는 올라가지 않는다. 하나만 걸려도 최대로 올리면 질문에 답할수록 결과가 달라지지 않아
 * 설문이 무의미해지고, 실제로 필요한 것보다 높은 사양이 나온다.
 * <p>
 * 답변이 없는 경우를 따로 분기하지 않는다. 신호가 하나도 안 걸린 결과가 곧 기본 권장 사양이다.
 * 분기를 두면 답변 전 화면과 답변 후 계산이 따로 놀 수 있다.
 * <p>
 * 가중치와 경계는 실사용량 추정에 근거한 잠정값이다 — 학습 단계는 16GB, 도커를 여러 개 띄우면 32GB,
 * 서비스를 상시 여러 개 올리면 48GB 로 본다. 사용자 반응을 보고 조정한다.
 */
@Component
public class LaptopBackendAlgorithm implements RecommendationAlgorithm {

    // 근거 맵의 키. LaptopSpec 이 내보내는 사양 항목 코드와 같아야 화면이 근거를 항목에 붙일 수 있다
    private static final String OS_ITEM = "OS";
    private static final String CPU_ITEM = "REQUIRED_CPU";
    private static final String MEMORY_ITEM = "MEMORY";
    private static final String STORAGE_ITEM = "STORAGE";

    // 답변이 없을 때 나가는 기본 권장 사양. 신호가 하나도 안 걸리면 이 값이 그대로 결과가 된다
    private static final Map<Os, CpuTier> BASELINE_CPU = Map.of(
        Os.MAC, CpuTier.BASIC,
        Os.WINDOWS, CpuTier.P_HS);
    private static final int BASELINE_MEMORY_GB = 16;
    private static final int BASELINE_STORAGE_GB = 512;

    // Mac 은 CPU 등급마다 살 수 있는 메모리가 정해져 있다
    private static final Map<CpuTier, List<Integer>> MAC_MEMORY_BY_CPU = Map.of(
        CpuTier.BASIC, List.of(16, 24, 32),
        CpuTier.PRO, List.of(24, 48));

    @Override
    public UsagePurposeCode purpose() {
        return UsagePurposeCode.BACKEND_DEVELOPMENT;
    }

    @Override
    public List<RecommendedSpec> recommend(Answers answers) {
        return targetOsList(answers).stream()
            .map(os -> recommendFor(os, answers))
            .toList();
    }

    private List<Os> targetOsList(Answers answers) {
        if (answers.has(PREFERRED_OS, PreferredOs.MACOS)) {
            return List.of(Os.MAC);
        }
        if (answers.has(PREFERRED_OS, PreferredOs.WINDOWS)) {
            return List.of(Os.WINDOWS);
        }
        return List.of(Os.MAC, Os.WINDOWS);
    }

    private RecommendedSpec recommendFor(Os os, Answers answers) {
        Map<String, List<String>> reasons = new HashMap<>();
        reasons.put(OS_ITEM, startWith(os.getDisplayName() + " 권장안입니다."));

        int memoryGb = calculateMemory(answers, startWith(reasons, MEMORY_ITEM));
        int storageGb = calculateStorage(answers, startWith(reasons, STORAGE_ITEM));
        CpuTier cpuTier = calculateCpu(os, answers, startWith(reasons, CPU_ITEM));

        LaptopSpec spec = buyableSpec(os, cpuTier, memoryGb, storageGb, reasons);
        addResultReasons(spec, answers, reasons);
        return new RecommendedSpec(spec, reasons);
    }

    /**
     * 결과 문장은 살 수 있는 조합으로 맞춘 뒤에 적는다. 앞서 적으면 buyableSpec 이 바꾼 값과 어긋난다.
     * 쓰던 CPU 와의 비교도 여기서 한다 — 올린 등급이 Mac 제한에 걸려 되내려올 수 있다.
     */
    private void addResultReasons(LaptopSpec spec, Answers answers, Map<String, List<String>> reasons) {
        List<String> cpuReasons = reasons.get(CPU_ITEM);
        CpuTier current = currentCpuTier(spec.os(), answers);
        if (current != null && feltDiscomfort(answers) && spec.cpuTier().isHigherThan(current)) {
            cpuReasons.add("지금 쓰는 " + current.getDisplayName() + " 에서 불편을 겪어 그보다 위를 권합니다.");
        }
        cpuReasons.add("권장 CPU 는 " + spec.cpuTier().getDisplayName() + " 입니다.");
        reasons.get(MEMORY_ITEM).add("권장 메모리는 " + spec.memoryGb() + "GB 입니다.");
        reasons.get(STORAGE_ITEM).add("권장 저장 공간은 " + spec.storageGb() + "GB 입니다.");
    }

    private int calculateMemory(Answers answers, List<String> reasons) {
        int signals = weigh(reasons,
            signal(usesJavaFamily(answers), 1,
                "Java·Kotlin·C# 계열은 빌드와 실행에 메모리를 더 씁니다."),
            signal(answers.has(IDE, Ide.JETBRAINS), 1,
                "JetBrains IDE 는 인덱싱과 코드 분석에 메모리를 많이 씁니다."),
            signal(answers.has(IDE, Ide.MULTIPLE), 2,
                "여러 IDE 를 함께 띄우면 그만큼 더 듭니다."),
            signal(answers.has(AI_CODING_TOOL, AiCodingTool.AI_EDITOR), 1,
                "AI 전용 에디터는 모델 연동으로 메모리를 더 씁니다."),
            signal(runsManyEnvironments(answers), 2,
                "개발 환경을 여러 개 띄우는 방식이라 여유가 필요합니다."),
            signal(runsFewEnvironments(answers), 1,
                "개발 환경을 띄워 두면 그만큼 메모리를 차지합니다."),
            signal(answers.has(SLOWDOWN, Slowdown.OFTEN), 2,
                "자주 느려진 경험은 메모리가 부족하다는 가장 직접적인 신호입니다."),
            signal(answers.has(SLOWDOWN, Slowdown.SOMETIMES), 1,
                "가끔 느려진 경험을 반영했습니다."),
            signal(usesLong(answers), 1,
                "오래 쓸 계획이라 여유를 두었습니다."),
            signal(worksRemotely(answers), -2,
                "원격 서버에서 개발하면 로컬 메모리 부담이 적습니다."),
            signal(usesShort(answers), -1,
                "짧게 쓸 계획이라 과한 용량을 피했습니다."));

        return memoryFor(signals);
    }

    private int memoryFor(int signals) {
        if (signals <= 1) {
            return BASELINE_MEMORY_GB;
        }
        if (signals <= 3) {
            return 24;
        }
        if (signals <= 6) {
            return 32;
        }
        return 48;
    }

    private int calculateStorage(Answers answers, List<String> reasons) {
        // 지금 쓰는 SSD 가 작다는 걸 아는 경우의 용량 부족은 디스크 크기 탓이라 상향 근거로 쓰지 않는다.
        // 미입력은 작다는 근거가 없으므로 사용자의 자기 보고를 그대로 인정한다.
        boolean shortageCountsUp = !answers.isAnswered(CURRENT_STORAGE) || hasAtLeast512Ssd(answers);

        int signals = weigh(reasons,
            signal(answers.has(DEV_ENVIRONMENT_SETUP, DevEnvironmentSetup.DOCKER_MANY), 3,
                "컨테이너 이미지가 쌓이면 저장 공간을 크게 씁니다."),
            signal(runsFewEnvironments(answers)
                    || answers.has(DEV_ENVIRONMENT_SETUP, DevEnvironmentSetup.LOCAL_MANY), 1,
                "개발 환경과 이미지가 자리를 차지합니다."),
            signal(answers.has(PROGRAMMING_LANGUAGE, ProgrammingLanguage.NODE_TYPESCRIPT), 1,
                "Node·TypeScript 프로젝트는 의존성이 많아 자리를 차지합니다."),
            signal(shortageCountsUp && answers.has(STORAGE_SHORTAGE, StorageShortage.OFTEN), 2,
                "용량이 자주 부족했던 경험을 반영했습니다."),
            signal(shortageCountsUp && answers.has(STORAGE_SHORTAGE, StorageShortage.ONCE_OR_TWICE), 1,
                "용량이 부족했던 경험을 반영했습니다."),
            signal(usesLong(answers), 1,
                "오래 쓸 계획이라 여유를 두었습니다."),
            signal(hasSpareSsd(answers), -1,
                "지금 1TB 를 쓰면서 용량이 부족한 적이 없었습니다."),
            signal(worksRemotely(answers), -2,
                "원격 서버에서 개발하면 로컬 저장 공간 부담이 적습니다."),
            signal(usesShort(answers), -1,
                "짧게 쓸 계획이라 과한 용량을 피했습니다."));

        if (!shortageCountsUp && answers.isAnswered(STORAGE_SHORTAGE)) {
            reasons.add("지금 쓰는 SSD 가 작아 용량 부족은 디스크 크기 탓으로 보고 수치에 반영하지 않았습니다.");
        }

        return storageFor(signals);
    }

    private int storageFor(int signals) {
        if (signals <= -2) {
            return 256;
        }
        if (signals <= 2) {
            return BASELINE_STORAGE_GB;
        }
        return 1024;
    }

    private CpuTier calculateCpu(Os os, Answers answers, List<String> reasons) {
        int signals = weigh(reasons,
            signal(usesJavaFamily(answers), 1,
                "Java·Kotlin·C# 계열은 빌드에 CPU 를 많이 씁니다."),
            signal(answers.has(BUILD_WAIT, BuildWait.OFTEN), 2,
                "빌드·테스트 대기가 답답했던 경험을 반영했습니다."),
            signal(answers.has(OVERHEATING, Overheating.OFTEN), 2,
                "지속 부하에서 발열로 성능이 떨어진 경험을 반영했습니다."),
            signal(answers.has(DEV_ENVIRONMENT_SETUP, DevEnvironmentSetup.DOCKER_MANY), 1,
                "여러 컨테이너를 동시에 띄우면 코어가 더 필요합니다."),
            signal(answers.has(IDE, Ide.MULTIPLE), 1,
                "여러 IDE 를 함께 쓰면 백그라운드 작업이 겹칩니다."),
            signal(usesLong(answers), 1,
                "오래 쓸 계획이라 여유를 두었습니다."),
            signal(worksRemotely(answers), -2,
                "원격 서버에서 빌드하면 로컬 CPU 부담이 적습니다."),
            signal(usesShort(answers), -1,
                "짧게 쓸 계획이라 과한 사양을 피했습니다."));

        CpuTier tier = raise(BASELINE_CPU.get(os), cpuStepsFor(signals));
        tier = atLeastAboveCurrent(os, tier, answers);

        if (os == Os.MAC && tier == CpuTier.MAX) {
            reasons.add("예산을 고려해 Mac 권장 CPU 는 M Pro 칩으로 제한했습니다.");
            tier = CpuTier.PRO;
        }
        return tier;
    }

    private int cpuStepsFor(int signals) {
        if (signals <= 2) {
            return 0;
        }
        if (signals <= 4) {
            return 1;
        }
        return 2;
    }

    /**
     * 쓰던 노트북과 같은 OS 를 권할 때, 빌드 대기나 발열을 겪었다면 그 CPU 한 단계 위를 밑돌지 않게 한다.
     */
    private CpuTier atLeastAboveCurrent(Os os, CpuTier tier, Answers answers) {
        if (!feltDiscomfort(answers)) {
            return tier;
        }
        CpuTier current = currentCpuTier(os, answers);
        if (current == null) {
            return tier;
        }
        return current.stepUp().higherOf(tier);
    }

    private boolean feltDiscomfort(Answers answers) {
        return answers.has(BUILD_WAIT, BuildWait.OFTEN) || answers.has(OVERHEATING, Overheating.OFTEN);
    }

    /**
     * Mac 은 CPU 등급마다 살 수 있는 메모리가 정해져 있다. 살 수 없는 조합이면 올려서 맞춘다.
     */
    private LaptopSpec buyableSpec(Os os, CpuTier cpuTier, int memoryGb, int storageGb,
                                   Map<String, List<String>> reasons) {
        if (os != Os.MAC) {
            return new LaptopSpec(os, cpuTier, memoryGb, storageGb);
        }
        CpuTier tier = cpuTier;
        if (memoryGb > largestMemoryOf(tier)) {
            tier = tier.stepUp();
            reasons.get(CPU_ITEM)
                .add(memoryGb + "GB 를 쓰려면 " + tier.getDisplayName() + " 이상이어야 합니다.");
        }
        int adjusted = smallestMemoryAtLeast(tier, memoryGb);
        if (adjusted != memoryGb) {
            reasons.get(MEMORY_ITEM)
                .add(tier.getDisplayName() + " 에서 고를 수 있는 가장 가까운 용량은 " + adjusted + "GB 입니다.");
        }
        return new LaptopSpec(os, tier, adjusted, storageGb);
    }

    private int largestMemoryOf(CpuTier tier) {
        return MAC_MEMORY_BY_CPU.get(tier).getLast();
    }

    private int smallestMemoryAtLeast(CpuTier tier, int memoryGb) {
        return MAC_MEMORY_BY_CPU.get(tier).stream()
            .filter(option -> option >= memoryGb)
            .findFirst()
            .orElseGet(() -> largestMemoryOf(tier));
    }

    private CpuTier raise(CpuTier tier, int steps) {
        CpuTier raised = tier;
        for (int step = 0; step < steps; step++) {
            raised = raised.stepUp();
        }
        return raised;
    }

    private CpuTier currentCpuTier(Os os, Answers answers) {
        OptionCode answer = answers.answerTo(currentCpuQuestionOf(os));
        if (answer == null) {
            return null;
        }
        // 현재 CPU 선택지와 등급은 이름이 같다 (CpuTierTest 가 지킨다)
        return CpuTier.valueOf(answer.name());
    }

    private QuestionCode currentCpuQuestionOf(Os os) {
        if (os == Os.MAC) {
            return CURRENT_MAC_CPU;
        }
        return CURRENT_WINDOWS_CPU;
    }

    private boolean usesJavaFamily(Answers answers) {
        return answers.has(PROGRAMMING_LANGUAGE, ProgrammingLanguage.JAVA_FAMILY);
    }

    private boolean runsManyEnvironments(Answers answers) {
        return answers.hasAnyOf(DEV_ENVIRONMENT_SETUP,
            DevEnvironmentSetup.LOCAL_MANY, DevEnvironmentSetup.DOCKER_MANY);
    }

    private boolean runsFewEnvironments(Answers answers) {
        return answers.hasAnyOf(DEV_ENVIRONMENT_SETUP,
            DevEnvironmentSetup.LOCAL_FEW, DevEnvironmentSetup.DOCKER_FEW);
    }

    private boolean worksRemotely(Answers answers) {
        return answers.has(DEV_ENVIRONMENT_SETUP, DevEnvironmentSetup.REMOTE);
    }

    private boolean usesLong(Answers answers) {
        return answers.has(USAGE_PERIOD, UsagePeriod.FIVE_PLUS_YEARS);
    }

    private boolean usesShort(Answers answers) {
        return answers.has(USAGE_PERIOD, UsagePeriod.TWO_YEARS);
    }

    private boolean hasAtLeast512Ssd(Answers answers) {
        return answers.hasAnyOf(CURRENT_STORAGE, CurrentStorage.UNDER_1TB, CurrentStorage.TB_1_OR_MORE);
    }

    private boolean hasSpareSsd(Answers answers) {
        return answers.has(CURRENT_STORAGE, CurrentStorage.TB_1_OR_MORE)
            && answers.has(STORAGE_SHORTAGE, StorageShortage.NEVER);
    }

    private List<String> startWith(Map<String, List<String>> reasons, String itemCode) {
        List<String> itemReasons = new ArrayList<>();
        reasons.put(itemCode, itemReasons);
        return itemReasons;
    }

    private List<String> startWith(String reason) {
        List<String> reasons = new ArrayList<>();
        reasons.add(reason);
        return reasons;
    }

    private int weigh(List<String> reasons, Signal... signals) {
        int total = 0;
        for (Signal each : signals) {
            if (each.matched()) {
                total += each.weight();
                reasons.add(each.reason());
            }
        }
        return total;
    }

    private Signal signal(boolean matched, int weight, String reason) {
        return new Signal(matched, weight, reason);
    }

    /**
     * 사양을 올리거나 내리는 근거 하나. 걸린 신호의 가중치를 더해 단계를 정한다.
     */
    private record Signal(boolean matched, int weight, String reason) {
    }
}
