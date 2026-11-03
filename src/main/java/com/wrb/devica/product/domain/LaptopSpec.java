package com.wrb.devica.product.domain;

import java.util.List;

public record LaptopSpec(Os os, CpuTier cpuTier, int memoryGb, int storageGb) implements Spec {

    public static final List<Integer> MEMORY_STEPS = List.of(16, 24, 32, 48);
    public static final List<Integer> STORAGE_STEPS = List.of(256, 512, 1024, 2048);

    public LaptopSpec {
        if (cpuTier.getOs() != os) {
            throw new IllegalArgumentException("CPU 등급이 OS 와 맞지 않습니다: " + os + ", " + cpuTier);
        }

        if (!MEMORY_STEPS.contains(memoryGb) || !STORAGE_STEPS.contains(storageGb)) {
            throw new IllegalArgumentException("권장 사양에 없는 메모리나 저장 공간입니다: " + memoryGb + ", " + storageGb);
        }
    }

    @Override
    public List<SpecValue> values() {
        return List.of(
            new SpecValue("OS", os.name()),
            new SpecValue("REQUIRED_CPU", cpuTier.name()),
            new SpecValue("MEMORY", String.valueOf(memoryGb)),
            new SpecValue("STORAGE", String.valueOf(storageGb)));
    }
}
