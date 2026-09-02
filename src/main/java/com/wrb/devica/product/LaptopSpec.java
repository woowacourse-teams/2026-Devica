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
    public List<SpecItem> toItems() {
        return List.of(
            new SpecItem("OS", "운영체제", os.name(), os.getDisplayName()),
            new SpecItem("CPU", "CPU", cpu, cpu),
            new SpecItem("MEMORY", "메모리", String.valueOf(memoryGb), memoryGb + "GB"),
            new SpecItem("STORAGE", "저장 공간", String.valueOf(storageGb), storageGb + "GB"));
    }
}
