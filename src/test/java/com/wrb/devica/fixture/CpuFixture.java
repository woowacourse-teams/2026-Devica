package com.wrb.devica.fixture;

import static java.util.Objects.requireNonNullElse;

import com.wrb.devica.product.Cpu;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CpuFixture {

    @Builder(builderMethodName = "cpu")
    private static Cpu cpuBuilder(String manufacturer, String name, Integer coreCount, Integer score) {
        int targetScore = requireNonNullElse(score, 10000);

        return Cpu.builder()
            .manufacturer(requireNonNullElse(manufacturer, "Intel"))
            .name(requireNonNullElse(name, "Core " + targetScore))
            .coreCount(requireNonNullElse(coreCount, 8))
            .score(targetScore)
            .build();
    }
}
