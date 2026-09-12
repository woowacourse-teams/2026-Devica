package com.wrb.devica.product;

import java.util.List;

public record LaptopSpec(Os os, String cpu, int memoryGb, int storageGb) implements Spec {

    public LaptopSpec {
        if (cpu.isBlank()) {
            throw new IllegalArgumentException("CPU 는 비어 있을 수 없습니다.");
        }
        if (memoryGb <= 0 || storageGb <= 0) {
            throw new IllegalArgumentException("메모리와 저장 공간은 0보다 커야 합니다: " + memoryGb + ", " + storageGb);
        }
    }

    @Override
    public List<SpecValue> values() {
        return List.of(
            new SpecValue("OS", os.name()),
            new SpecValue("CPU", cpu),
            new SpecValue("MEMORY", String.valueOf(memoryGb)),
            new SpecValue("STORAGE", String.valueOf(storageGb)));
    }
}
