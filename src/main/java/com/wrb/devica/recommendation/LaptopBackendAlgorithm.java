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

import com.wrb.devica.product.CpuTier;
import com.wrb.devica.product.LaptopSpec;
import com.wrb.devica.product.Os;
import com.wrb.devica.purpose.UsagePurposeCode;
import com.wrb.devica.question.OptionCode;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 백엔드 개발 노트북 추천. 기준 사양에서 시작해 답변에 따라 항목별로 올리거나 내린다.
 * <p>
 * 상향 조건과 하향 조건이 함께 걸리면 기준값을 유지한다 — 어느 쪽이 더 센지 판단할 근거가 없다.
 */
@Component
public class LaptopBackendAlgorithm implements RecommendationAlgorithm {

    private static final Map<Os, CpuTier> BASELINE_CPU = Map.of(
        Os.MAC, CpuTier.BASIC,
        Os.WINDOWS, CpuTier.P_HS);
    private static final int BASELINE_MEMORY_GB = 24;
    private static final int BASELINE_STORAGE_GB = 512;

    // Mac 은 CPU 등급마다 살 수 있는 메모리 조합이 정해져 있다
    private static final Map<CpuTier, List<Integer>> MAC_MEMORY_BY_CPU = Map.of(
        CpuTier.BASIC, List.of(16, 24, 32),
        CpuTier.PRO, List.of(24, 48));

    private static final String KEPT_BY_CONFLICT = "상향 조건과 하향 조건이 함께 있어 기준값을 유지했습니다.";
    private static final String LONG_USE_SUPPORTS = "오래 사용할 계획이 상향 판단을 보강했습니다.";
    private static final String LONG_USE_REFERENCE = "오래 사용할 계획은 단독 상향 대신 참고 근거로만 반영했습니다.";

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

    // 선호 OS 를 안 밝혔으면 Mac·Windows 권장안을 함께 낸다
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
        Map<String, List<String>> reasons = new LinkedHashMap<>();
        reasons.put("OS", startWith(os.getDisplayName() + " 권장안입니다."));
        reasons.put("CPU_TIER", startWith(os.getDisplayName() + " 백엔드 개발 기본 CPU 입니다."));
        reasons.put("MEMORY", startWith(os.getDisplayName() + " 기본 권장 메모리에서 시작했습니다."));
        reasons.put("STORAGE", startWith("백엔드 개발 기본 저장 공간에서 시작했습니다."));

        int memoryGb = calculateMemory(os, answers, reasons.get("MEMORY"));
        int storageGb = calculateStorage(os, answers, reasons.get("STORAGE"));
        CpuTier cpuTier = alignCpuToMemory(
            os, calculateCpu(os, answers, reasons.get("CPU_TIER")), memoryGb, reasons.get("CPU_TIER"));

        return new RecommendedSpec(new LaptopSpec(os, cpuTier, memoryGb, storageGb), Map.copyOf(reasons));
    }

    private List<String> startWith(String baselineReason) {
        List<String> reasons = new ArrayList<>();
        reasons.add(baselineReason);
        return reasons;
    }

    private int calculateMemory(Os os, Answers answers, List<String> reasons) {
        boolean fullUp = usesJavaFamily(answers)
            || answers.hasAnyOf(IDE, Ide.JETBRAINS, Ide.MULTIPLE)
            || answers.has(AI_CODING_TOOL, AiCodingTool.AI_EDITOR)
            || answers.hasAnyOf(DEV_ENVIRONMENT_SETUP,
                DevEnvironmentSetup.LOCAL_MANY, DevEnvironmentSetup.DOCKER_MANY)
            || answers.has(SLOWDOWN, Slowdown.OFTEN);
        boolean halfUp = !fullUp && answers.has(SLOWDOWN, Slowdown.SOMETIMES);
        boolean down = answers.has(DEV_ENVIRONMENT_SETUP, DevEnvironmentSetup.REMOTE)
            && answers.has(USAGE_PERIOD, UsagePeriod.TWO_YEARS);

        if ((fullUp || halfUp) && down) {
            reasons.add(KEPT_BY_CONFLICT);
            return BASELINE_MEMORY_GB;
        }
        if (fullUp) {
            int increment = os == Os.MAC ? 24 : 16;
            reasons.add("개발 도구와 작업 부하를 고려해 메모리를 " + increment + "GB 높였습니다.");
            addLongUseSupport(answers, reasons);
            return BASELINE_MEMORY_GB + increment;
        }
        if (halfUp) {
            reasons.add("가끔 발생한 메모리 부족 경험을 반영해 8GB 높였습니다.");
            addLongUseSupport(answers, reasons);
            return BASELINE_MEMORY_GB + 8;
        }
        if (down) {
            reasons.add("원격 개발과 짧은 사용 계획이 함께 확인되어 8GB 낮췄습니다.");
            return BASELINE_MEMORY_GB - 8;
        }
        addLongUseReference(answers, reasons);
        return BASELINE_MEMORY_GB;
    }

    private int calculateStorage(Os os, Answers answers, List<String> reasons) {
        OptionCode currentStorage = answers.single(CURRENT_STORAGE);
        boolean atLeast512 = currentStorage == CurrentStorage.UNDER_1TB
            || currentStorage == CurrentStorage.TB_1_OR_MORE;
        // 현재 SSD 가 작다는 걸 아는 경우의 용량 부족 경험은 디스크 크기 탓이라 상향 근거로 쓰지 않는다.
        // 미입력은 작다는 근거가 없으므로 사용자의 자기 보고를 그대로 인정한다.
        boolean smallSsdKnown = currentStorage != null && !atLeast512;

        boolean up = answers.has(PROGRAMMING_LANGUAGE, ProgrammingLanguage.NODE_TYPESCRIPT)
            || answers.hasAnyOf(DEV_ENVIRONMENT_SETUP, DevEnvironmentSetup.LOCAL_MANY,
                DevEnvironmentSetup.DOCKER_MANY, DevEnvironmentSetup.DOCKER_FEW)
            || (!smallSsdKnown && answers.has(STORAGE_SHORTAGE, StorageShortage.OFTEN));
        int downCount = count(answers.has(DEV_ENVIRONMENT_SETUP, DevEnvironmentSetup.REMOTE))
            + count(atLeast512 && answers.has(STORAGE_SHORTAGE, StorageShortage.NEVER))
            + count(answers.has(USAGE_PERIOD, UsagePeriod.TWO_YEARS));
        boolean down = downCount >= 2;

        if (up && down) {
            reasons.add(KEPT_BY_CONFLICT);
            return BASELINE_STORAGE_GB;
        }
        if (up) {
            reasons.add("프로젝트와 개발 환경의 저장 공간 사용량을 고려해 1TB를 권장합니다.");
            addLongUseSupport(answers, reasons);
            return 1024;
        }
        if (down) {
            if (os == Os.MAC) {
                reasons.add("Mac 권장 사양 범위에 맞춰 512GB를 유지했습니다.");
                return BASELINE_STORAGE_GB;
            }
            reasons.add("저장 공간 하향 조건이 두 개 이상 확인되어 256GB로 조정했습니다.");
            return 256;
        }
        if (!atLeast512 && answers.single(STORAGE_SHORTAGE) != null) {
            reasons.add("현재 SSD가 미입력이거나 512GB 상당 미만이라 용량 경험은 수치에 반영하지 않았습니다.");
        }
        addLongUseReference(answers, reasons);
        return BASELINE_STORAGE_GB;
    }

    private CpuTier calculateCpu(Os os, Answers answers, List<String> reasons) {
        CpuTier tier = BASELINE_CPU.get(os);
        if (usesJavaFamily(answers)) {
            tier = tier.stepUp();
            reasons.add("Java·Kotlin·C# 계열의 빌드 부하를 고려해 한 단계 높였습니다.");
        }
        if (answers.has(BUILD_WAIT, BuildWait.OFTEN)) {
            tier = upgradeByExperience(os, tier, answers);
            reasons.add("빌드·테스트 대기 경험을 반영했습니다.");
        }
        if (answers.has(OVERHEATING, Overheating.OFTEN)) {
            tier = upgradeByExperience(os, tier, answers);
            reasons.add(os == Os.MAC
                ? "지속 부하와 발열 경험을 반영해 Pro 이상 등급을 검토했습니다."
                : "지속 부하와 발열 경험을 반영해 H·HX 계열을 검토했습니다.");
        }
        if (os == Os.MAC && tier == CpuTier.MAX) {
            reasons.add("예산 범위를 고려해 Mac 권장 CPU는 M Pro 칩으로 제한했습니다.");
            return CpuTier.PRO;
        }
        return tier;
    }

    // 쓰던 노트북이 권장안과 같은 OS 면, 그 CPU 보다 한 단계 위를 밑돌지 않게 한다
    private CpuTier upgradeByExperience(Os os, CpuTier recommended, Answers answers) {
        CpuTier current = currentCpuTier(os, answers);
        if (current == null) {
            return recommended.stepUp();
        }
        return current.stepUp().higherOf(recommended);
    }

    private CpuTier currentCpuTier(Os os, Answers answers) {
        if (os == Os.MAC && answers.has(CURRENT_OS, CurrentOs.MACOS)) {
            return toTier(answers.single(CURRENT_MAC_CPU));
        }
        if (os == Os.WINDOWS && answers.has(CURRENT_OS, CurrentOs.WINDOWS)) {
            return toTier(answers.single(CURRENT_WINDOWS_CPU));
        }
        return null;
    }

    // 현재 CPU 선택지와 권장 등급은 같은 사다리를 가리키므로 이름으로 잇는다
    private CpuTier toTier(OptionCode answer) {
        if (answer instanceof CurrentMacCpu || answer instanceof CurrentWindowsCpu) {
            return CpuTier.valueOf(answer.name());
        }
        return null;
    }

    // Mac 은 등급과 메모리 조합이 제한적이라, 계산된 메모리를 지원하는 등급이 하나뿐이면 그쪽으로 맞춘다
    private CpuTier alignCpuToMemory(Os os, CpuTier cpuTier, int memoryGb, List<String> reasons) {
        if (os != Os.MAC) {
            return cpuTier;
        }
        List<CpuTier> supporting = MAC_MEMORY_BY_CPU.entrySet().stream()
            .filter(entry -> entry.getValue().contains(memoryGb))
            .map(Map.Entry::getKey)
            .toList();
        if (supporting.size() != 1 || supporting.getFirst() == cpuTier) {
            return cpuTier;
        }
        CpuTier aligned = supporting.getFirst();
        reasons.add(memoryGb + "GB RAM 지원 조합에 맞춰 " + aligned.getDisplayName() + "으로 조정했습니다.");
        return aligned;
    }

    private boolean usesJavaFamily(Answers answers) {
        return answers.has(PROGRAMMING_LANGUAGE, ProgrammingLanguage.JAVA_FAMILY);
    }

    private void addLongUseSupport(Answers answers, List<String> reasons) {
        if (answers.has(USAGE_PERIOD, UsagePeriod.FIVE_PLUS_YEARS)) {
            reasons.add(LONG_USE_SUPPORTS);
        }
    }

    private void addLongUseReference(Answers answers, List<String> reasons) {
        if (answers.has(USAGE_PERIOD, UsagePeriod.FIVE_PLUS_YEARS)) {
            reasons.add(LONG_USE_REFERENCE);
        }
    }

    private int count(boolean condition) {
        return condition ? 1 : 0;
    }
}
