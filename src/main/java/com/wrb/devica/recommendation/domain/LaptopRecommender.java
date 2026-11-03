package com.wrb.devica.recommendation.domain;

import com.wrb.devica.product.domain.CpuTier;
import com.wrb.devica.product.domain.LaptopSpec;
import com.wrb.devica.product.domain.Os;
import com.wrb.devica.product.domain.StorageSize;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class LaptopRecommender {

    private static final String OS_ITEM = "OS";
    private static final String CPU_ITEM = LaptopRequirement.REQUIRED_CPU.name();
    private static final String MEMORY_ITEM = LaptopRequirement.MEMORY.name();
    private static final String STORAGE_ITEM = LaptopRequirement.STORAGE.name();

    private static final Map<CpuTier, List<Integer>> MAC_MEMORY_BY_CPU = Map.of(
        CpuTier.BASIC, List.of(16, 24, 32),
        CpuTier.PRO, List.of(24, 48));

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

        return errors;
    }

    private static void validateCpu(Map<Os, List<ScoreRange<CpuTier>>> cpu, List<String> errors) {
        if (cpu == null) {
            errors.add("CPU 구간이 없습니다");
            return;
        }

        for (Os os : Os.values()) {
            String where = "CPU 구간 " + os;
            List<ScoreRange<CpuTier>> ranges = cpu.get(os);
            ScoreRange.validate(ranges, where, errors);

            if (ranges != null) {
                ranges.stream()
                    .map(ScoreRange::value)
                    .filter(tier -> tier != null && tier.getOs() != os)
                    .forEach(tier -> errors.add(where + ": " + os + " 의 CPU 등급이 아닙니다: " + tier));
            }
        }
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

        // 상한에서 고를 수 없는 메모리가 나오면 메모리 보정이 CPU 를 상한 위로 다시 올린다
        int largest = MAC_MEMORY_BY_CPU.get(limit).getLast();
        memory.stream()
            .map(ScoreRange::value)
            .filter(value -> value != null && value > largest)
            .max(Integer::compare)
            .ifPresent(value -> errors.add(
                "Mac CPU 상한 " + limit + " 에서는 " + value + "GB 를 고를 수 없습니다. 최대 " + largest + "GB 입니다"));
    }

    public List<RecommendedSpec> recommend(LaptopInterpretation interpretation, Requirements requirements,
                                           Map<String, String> conditions) {
        return Stream.of(Os.MAC, Os.WINDOWS)
            .map(os -> recommendFor(interpretation, os, requirements, conditions))
            .toList();
    }

    private RecommendedSpec recommendFor(LaptopInterpretation interpretation, Os os, Requirements requirements,
                                         Map<String, String> conditions) {
        Map<String, List<String>> reasons = new HashMap<>();
        reasons.put(OS_ITEM, new ArrayList<>(List.of(os.getDisplayName() + " 권장안입니다.")));
        for (LaptopRequirement requirement : LaptopRequirement.values()) {
            reasons.put(requirement.name(), new ArrayList<>(requirements.reasonsOf(requirement.name())));
        }

        int memoryGb = ScoreRange.valueFor(interpretation.memory(), requirements.valueOf(MEMORY_ITEM));
        int storageGb = ScoreRange.valueFor(interpretation.storage(), requirements.valueOf(STORAGE_ITEM));
        CpuTier current = currentCpuTier(os, conditions);
        CpuTier cpuTier = calculateCpu(interpretation, os, requirements.valueOf(CPU_ITEM), current, reasons.get(CPU_ITEM));

        LaptopSpec spec = buyableSpec(os, cpuTier, memoryGb, storageGb, reasons);
        addResultReasons(spec, current, reasons);
        boolean preferred = os.name().equals(conditions.get(LaptopCondition.PREFERRED_OS.name()));
        return new RecommendedSpec(spec, reasons, preferred);
    }

    private CpuTier calculateCpu(LaptopInterpretation interpretation, Os os, int score, CpuTier current,
                                 List<String> reasons) {
        CpuTier tier = ScoreRange.valueFor(interpretation.cpu().get(os), score);
        if (current != null) {
            tier = current.stepUp().higherOf(tier);
        }
        CpuTier limit = interpretation.macCpuLimit();
        if (os == Os.MAC && tier.isHigherThan(limit)) {
            reasons.add("예산을 고려해 Mac 권장 CPU 는 " + limit.getDisplayName() + "으로 제한했습니다.");
            tier = limit;
        }
        return tier;
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

    private void addResultReasons(LaptopSpec spec, CpuTier current, Map<String, List<String>> reasons) {
        List<String> cpuReasons = reasons.get(CPU_ITEM);
        if (current != null && spec.cpuTier().isHigherThan(current)) {
            cpuReasons.add("지금 쓰는 " + current.getDisplayName() + " 에서 불편을 겪어 그보다 위를 권합니다.");
        }
        cpuReasons.add("권장 CPU 는 " + spec.cpuTier().getDisplayName() + " 입니다.");
        reasons.get(MEMORY_ITEM).add("권장 메모리는 " + spec.memoryGb() + "GB 입니다.");
        reasons.get(STORAGE_ITEM).add("권장 저장 공간은 " + StorageSize.display(spec.storageGb()) + " 입니다.");
    }

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
}
