package com.wrb.devica.recommendation.laptop;

import com.wrb.devica.product.domain.CpuTier;
import com.wrb.devica.product.domain.LaptopSpec;
import com.wrb.devica.product.domain.Os;
import com.wrb.devica.product.domain.StorageSize;
import com.wrb.devica.recommendation.domain.RecommendedSpec;
import com.wrb.devica.recommendation.judgement.Requirements;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class BackendDevelopmentRecommender {

    private static final String OS_ITEM = "OS";
    private static final String CPU_ITEM = LaptopRequirement.REQUIRED_CPU.name();
    private static final String MEMORY_ITEM = LaptopRequirement.MEMORY.name();
    private static final String STORAGE_ITEM = LaptopRequirement.STORAGE.name();

    private static final Map<Os, List<Band<CpuTier>>> CPU_BANDS = Map.of(
        Os.MAC, List.of(
            new Band<>(2, CpuTier.BASIC),
            new Band<>(4, CpuTier.PRO),
            new Band<>(Integer.MAX_VALUE, CpuTier.MAX)),
        Os.WINDOWS, List.of(
            new Band<>(2, CpuTier.P_HS),
            new Band<>(4, CpuTier.H),
            new Band<>(Integer.MAX_VALUE, CpuTier.HX)));

    private static final List<Band<Integer>> MEMORY_BANDS = List.of(
        new Band<>(1, 16),
        new Band<>(3, 24),
        new Band<>(6, 32),
        new Band<>(Integer.MAX_VALUE, 48));

    private static final List<Band<Integer>> STORAGE_BANDS = List.of(
        new Band<>(-2, 256),
        new Band<>(2, 512),
        new Band<>(Integer.MAX_VALUE, 1024));

    private static final Map<CpuTier, List<Integer>> MAC_MEMORY_BY_CPU = Map.of(
        CpuTier.BASIC, List.of(16, 24, 32),
        CpuTier.PRO, List.of(24, 48));

    private static <T> T valueFor(List<Band<T>> bands, int score) {
        return bands.stream()
            .filter(band -> score <= band.maxScore())
            .findFirst()
            .orElseThrow()
            .value();
    }

    public List<RecommendedSpec> recommend(Requirements requirements, Map<String, String> conditions) {
        return Stream.of(Os.MAC, Os.WINDOWS)
            .map(os -> recommendFor(os, requirements, conditions))
            .toList();
    }

    private RecommendedSpec recommendFor(Os os, Requirements requirements, Map<String, String> conditions) {
        Map<String, List<String>> reasons = new HashMap<>();
        reasons.put(OS_ITEM, new ArrayList<>(List.of(os.getDisplayName() + " 권장안입니다.")));
        for (LaptopRequirement requirement : LaptopRequirement.values()) {
            reasons.put(requirement.name(), new ArrayList<>(requirements.reasonsOf(requirement.name())));
        }

        int memoryGb = valueFor(MEMORY_BANDS, requirements.valueOf(MEMORY_ITEM));
        int storageGb = valueFor(STORAGE_BANDS, requirements.valueOf(STORAGE_ITEM));
        CpuTier current = currentCpuTier(os, conditions);
        CpuTier cpuTier = calculateCpu(os, requirements.valueOf(CPU_ITEM), current, reasons.get(CPU_ITEM));

        LaptopSpec spec = buyableSpec(os, cpuTier, memoryGb, storageGb, reasons);
        addResultReasons(spec, current, reasons);
        boolean preferred = os.name().equals(conditions.get(LaptopCondition.PREFERRED_OS.name()));
        return new RecommendedSpec(spec, reasons, preferred);
    }

    private CpuTier calculateCpu(Os os, int score, CpuTier current, List<String> reasons) {
        CpuTier tier = valueFor(CPU_BANDS.get(os), score);
        if (current != null) {
            tier = current.stepUp().higherOf(tier);
        }
        if (os == Os.MAC && !MAC_MEMORY_BY_CPU.containsKey(tier)) {
            reasons.add("예산을 고려해 Mac 권장 CPU 는 M Pro 칩으로 제한했습니다.");
            tier = CpuTier.PRO;
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

    private record Band<T>(int maxScore, T value) {
    }
}
