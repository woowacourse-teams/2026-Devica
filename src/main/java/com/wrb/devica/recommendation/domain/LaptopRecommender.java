package com.wrb.devica.recommendation.domain;

import com.wrb.devica.product.domain.ColorGamut;
import com.wrb.devica.product.domain.CpuTier;
import com.wrb.devica.product.domain.GpuTier;
import com.wrb.devica.product.domain.LaptopSpec;
import com.wrb.devica.product.domain.Os;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class LaptopRecommender {

    private static final String OS_ITEM = "OS";
    private static final String CPU_ITEM = LaptopRequirement.REQUIRED_CPU.name();
    private static final String MEMORY_ITEM = LaptopRequirement.MEMORY.name();
    private static final String STORAGE_ITEM = LaptopRequirement.STORAGE.name();
    private static final String COLOR_GAMUT_ITEM = LaptopRequirement.COLOR_GAMUT.name();
    private static final String GPU_ITEM = LaptopRequirement.REQUIRED_GPU.name();

    private static final Map<CpuTier, List<Integer>> MAC_MEMORY_BY_CPU = Map.of(
        CpuTier.BASIC, List.of(16, 24, 32),
        CpuTier.PRO, List.of(24, 48));

    private static final Map<GpuTier, CpuTier> MAC_CHIP_BY_GPU = Map.of(
        GpuTier.BASIC, CpuTier.BASIC,
        GpuTier.PRO, CpuTier.PRO,
        GpuTier.MAX, CpuTier.MAX);

    public static List<String> findUnsupported(LaptopInterpretation interpretation) {
        List<String> errors = new ArrayList<>();

        if (interpretation == null) {
            errors.add("점수 해석 규칙이 없습니다");
            return errors;
        }

        validateCpu(interpretation.cpu(), errors);

        validateSteps(interpretation.memory(), LaptopSpec.MEMORY_STEPS, "메모리 구간", errors);
        validateSteps(interpretation.storage(), LaptopSpec.STORAGE_STEPS, "저장 공간 구간", errors);

        validateMacCpuLimit(interpretation.macCpuLimit(), interpretation.memory(), errors);

        validateOptional(interpretation.colorGamut(), "화면 색 영역 구간", errors);
        validateGpu(interpretation.gpu(), errors);
        validateMacGpuLimit(interpretation.gpu(), interpretation.macCpuLimit(), errors);

        return errors;
    }

    private static void validateCpu(Map<Os, List<ScoreRange<CpuTier>>> cpu, List<String> errors) {
        if (cpu == null) {
            errors.add("CPU 구간이 없습니다");
            return;
        }

        validateByOs(cpu, CpuTier::getOs, "CPU", errors);
    }

    private static <T> void validateByOs(Map<Os, List<ScoreRange<T>>> rangesByOs, Function<T, Os> osOf,
                                         String item, List<String> errors) {
        for (Os os : Os.values()) {
            String where = item + " 구간 " + os;
            List<ScoreRange<T>> ranges = rangesByOs.get(os);
            ScoreRange.validate(ranges, where, errors);
            validateTierOs(ranges, os, osOf, item, where, errors);
        }
    }

    private static <T> void validateTierOs(List<ScoreRange<T>> ranges, Os os, Function<T, Os> osOf,
                                           String item, String where, List<String> errors) {
        if (ranges == null) {
            return;
        }

        ranges.stream()
            .map(ScoreRange::value)
            .filter(tier -> tier != null && osOf.apply(tier) != os)
            .forEach(tier -> errors.add(where + ": " + os + " 의 " + item + " 등급이 아닙니다: " + tier));
    }

    private static void validateSteps(List<ScoreRange<Integer>> ranges,
                                      List<Integer> steps, String where, List<String> errors) {
        ScoreRange.validate(ranges, where, errors);

        if (ranges != null) {
            ranges.stream()
                .map(ScoreRange::value)
                .filter(value -> value != null && !steps.contains(value))
                .forEach(value -> errors.add(where + ": " + steps + " 에 없는 값입니다: " + value));
        }
    }

    private static void validateMacCpuLimit(CpuTier limit, List<ScoreRange<Integer>> memory, List<String> errors) {
        if (limit == null || !MAC_MEMORY_BY_CPU.containsKey(limit)) {
            List<CpuTier> limits = CpuTier.inOrder().stream().filter(MAC_MEMORY_BY_CPU::containsKey).toList();
            errors.add("Mac CPU 상한은 " + limits + " 중 하나여야 합니다: " + limit);
            return;
        }

        if (memory == null) {
            return;
        }

        int largest = MAC_MEMORY_BY_CPU.get(limit).getLast();
        memory.stream()
            .map(ScoreRange::value)
            .filter(value -> value != null && value > largest)
            .max(Integer::compare)
            .ifPresent(value -> errors.add(
                "Mac CPU 상한 " + limit + " 에서는 " + value + "GB 를 고를 수 없습니다. 최대 " + largest + "GB 입니다"));
    }

    private static <T> void validateOptional(List<ScoreRange<T>> ranges, String where, List<String> errors) {
        if (ranges == null) {
            return;
        }

        ScoreRange.validate(ranges, where, errors);
    }

    private static void validateGpu(Map<Os, List<ScoreRange<GpuTier>>> gpu, List<String> errors) {
        if (gpu == null) {
            return;
        }

        validateByOs(gpu, GpuTier::getOs, "GPU", errors);
    }

    private static void validateMacGpuLimit(Map<Os, List<ScoreRange<GpuTier>>> gpu, CpuTier limit,
                                            List<String> errors) {
        if (gpu == null || limit == null || gpu.get(Os.MAC) == null) {
            return;
        }

        gpu.get(Os.MAC).stream()
            .map(ScoreRange::value)
            .filter(tier -> tier != null && MAC_CHIP_BY_GPU.containsKey(tier))
            .filter(tier -> MAC_CHIP_BY_GPU.get(tier).isHigherThan(limit))
            .forEach(tier -> errors.add("Mac GPU 구간의 " + tier + " 는 Mac CPU 상한 " + limit + " 보다 높습니다"));
    }

    public static List<String> findRulesWithoutRange(LaptopInterpretation interpretation, List<Rule> rules) {
        Map<String, Boolean> interpreted = Map.of(
            COLOR_GAMUT_ITEM, interpretation != null && interpretation.colorGamut() != null,
            GPU_ITEM, interpretation != null && interpretation.gpu() != null);

        return rules.stream()
            .map(Rule::criterion)
            .filter(criterion -> !interpreted.getOrDefault(criterion, true))
            .distinct()
            .map(criterion -> criterion + " 규칙이 있지만 점수 해석 규칙에 " + criterion + " 구간이 없습니다")
            .toList();
    }

    public List<RecommendedSpec> recommend(LaptopInterpretation interpretation,
                                           Function<Os, Requirements> requirementsOf,
                                           Map<String, String> conditions) {
        return Stream.of(Os.MAC, Os.WINDOWS)
            .map(os -> recommendFor(interpretation, os, requirementsOf.apply(os), conditions))
            .toList();
    }

    private RecommendedSpec recommendFor(LaptopInterpretation interpretation, Os os, Requirements requirements,
                                         Map<String, String> conditions) {
        Map<String, List<String>> reasons = new HashMap<>();
        reasons.put(OS_ITEM, new ArrayList<>());
        for (LaptopRequirement requirement : LaptopRequirement.values()) {
            reasons.put(requirement.name(), new ArrayList<>(requirements.reasonsOf(requirement.name())));
        }

        int memoryGb = ScoreRange.valueFor(interpretation.memory(), requirements.valueOf(MEMORY_ITEM));
        int storageGb = ScoreRange.valueFor(interpretation.storage(), requirements.valueOf(STORAGE_ITEM));
        CpuTier current = currentCpuTier(os, conditions);
        CpuTier cpuTier = calculateCpu(interpretation, os, requirements.valueOf(CPU_ITEM), current, reasons.get(CPU_ITEM));

        ColorGamut colorGamut = optionalValue(interpretation.colorGamut(), requirements.valueOf(COLOR_GAMUT_ITEM));

        GpuTier gpuTier = calculateGpu(interpretation, os, requirements.valueOf(GPU_ITEM));

        LaptopSpec spec = buyableSpec(os, cpuTier, memoryGb, storageGb, colorGamut, gpuTier, reasons);
        if (current != null && spec.cpuTier().isHigherThan(current)) {
            reasons.get(CPU_ITEM).add("지금 쓰는 " + current.getDisplayName() + "에서 불편을 겪어 그보다 위를 권합니다.");
        }
        boolean preferred = os.name().equals(conditions.get(LaptopCondition.PREFERRED_OS.name()));
        return new RecommendedSpec(spec, reasons, requirements.basis(), requirements.notes(), preferred);
    }

    private CpuTier currentCpuTier(Os os, Map<String, String> conditions) {
        String current = conditions.get(LaptopCondition.CURRENT_CPU_TIER.name());
        if (current == null) {
            return null;
        }
        CpuTier tier = CpuTier.valueOf(current);
        if (tier.getOs() != os) {
            return null;
        }
        return tier;
    }

    private CpuTier calculateCpu(LaptopInterpretation interpretation, Os os, int score, CpuTier current,
                                 List<String> reasons) {
        CpuTier tier = ScoreRange.valueFor(interpretation.cpu().get(os), score);

        if (current != null) {
            tier = current.stepUp().higherOf(tier);
        }

        CpuTier limit = interpretation.macCpuLimit();

        if (os != Os.MAC || !tier.isHigherThan(limit)) {
            return tier;
        }

        addMacCpuLimitReasons(limit, current, reasons);
        return limit;
    }

    private void addMacCpuLimitReasons(CpuTier limit, CpuTier current, List<String> reasons) {
        if (current != null && current.isHigherThan(limit)) {
            reasons.add("지금 쓰는 " + current.getDisplayName() + "보다 낮지만, 이 작업에는 "
                + limit.getDisplayName() + "이면 충분해 예산을 고려해 제한했습니다.");
            return;
        }

        reasons.add("예산을 고려해 Mac 권장 CPU는 " + limit.getDisplayName() + "으로 제한했습니다.");

        if (limit == current) {
            reasons.add("지금과 같은 " + limit.getDisplayName() + "이라도 최신 세대를 고르면 성능이 오릅니다.");
        }
    }

    private <T> T optionalValue(List<ScoreRange<T>> ranges, int score) {
        if (ranges == null) {
            return null;
        }
        return ScoreRange.valueFor(ranges, score);
    }

    private GpuTier calculateGpu(LaptopInterpretation interpretation, Os os, int score) {
        if (interpretation.gpu() == null) {
            return null;
        }
        return ScoreRange.valueFor(interpretation.gpu().get(os), score);
    }

    private LaptopSpec buyableSpec(Os os, CpuTier cpuTier, int memoryGb, int storageGb,
                                   ColorGamut colorGamut, GpuTier gpuTier, Map<String, List<String>> reasons) {
        if (os != Os.MAC) {
            return new LaptopSpec(os, cpuTier, memoryGb, storageGb, colorGamut, gpuTier);
        }

        CpuTier tier = matchCpuToGpu(cpuTier, gpuTier, reasons);

        if (memoryGb > largestMemoryOf(tier)) {
            tier = tier.stepUp();
            reasons.get(CPU_ITEM)
                .add(memoryGb + "GB를 쓰려면 " + tier.getDisplayName() + " 이상이어야 합니다.");
        }

        int adjusted = smallestMemoryAtLeast(tier, memoryGb);

        if (adjusted != memoryGb) {
            reasons.get(MEMORY_ITEM)
                .add(tier.getDisplayName() + "에서는 " + memoryGb + "GB를 고를 수 없어 바로 위 용량인 " + adjusted + "GB를 권합니다.");
        }

        return new LaptopSpec(os, tier, adjusted, storageGb, colorGamut, matchGpuToCpu(gpuTier, tier, reasons));
    }

    private CpuTier matchCpuToGpu(CpuTier cpuTier, GpuTier gpuTier, Map<String, List<String>> reasons) {
        if (gpuTier == null) {
            return cpuTier;
        }

        CpuTier chip = MAC_CHIP_BY_GPU.get(gpuTier);

        if (!chip.isHigherThan(cpuTier)) {
            return cpuTier;
        }

        reasons.get(CPU_ITEM)
            .add("GPU로 " + chip.getDisplayName() + "이 필요해 CPU도 " + chip.getDisplayName() + "을 권합니다.");
        return chip;
    }

    private GpuTier matchGpuToCpu(GpuTier gpuTier, CpuTier cpuTier, Map<String, List<String>> reasons) {
        if (gpuTier == null) {
            return null;
        }

        GpuTier chip = macGpuOf(cpuTier);

        if (chip.getMinScore() <= gpuTier.getMinScore()) {
            return gpuTier;
        }

        reasons.get(GPU_ITEM).add("CPU와 같은 칩이라 GPU도 " + chip.getDisplayName() + "입니다.");
        return chip;
    }

    private static GpuTier macGpuOf(CpuTier chip) {
        return MAC_CHIP_BY_GPU.entrySet().stream()
            .filter(entry -> entry.getValue() == chip)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElseThrow();
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
}
