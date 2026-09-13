package com.wrb.devica.product;

import java.util.List;

/**
 * 노트북에 권장하는 사양. 특정 제품이 아니라 "이 정도가 필요하다"는 요구를 담는다.
 */
public record LaptopSpec(Os os, CpuTier cpuTier, int memoryGb, int storageGb) implements Spec {

    public LaptopSpec {
        if (cpuTier.getOs() != os) {
            throw new IllegalArgumentException("CPU 등급이 OS 와 맞지 않습니다: " + os + ", " + cpuTier);
        }
        if (memoryGb <= 0 || storageGb <= 0) {
            throw new IllegalArgumentException("메모리와 저장 공간은 0보다 커야 합니다: " + memoryGb + ", " + storageGb);
        }
    }

    @Override
    public List<SpecValue> values() {
        return List.of(
            new SpecValue("OS", os.name()),
            new SpecValue("CPU_TIER", cpuTier.name()),
            new SpecValue("MEMORY", String.valueOf(memoryGb)),
            new SpecValue("STORAGE", String.valueOf(storageGb)));
    }
}
