package com.wrb.devica.fixture;

import static java.util.Objects.requireNonNullElse;

import com.wrb.devica.product.domain.Gpu;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GpuFixture {

    public static GpuBuilder gpu() {
        return builder();
    }

    @Builder
    private static Gpu gpuBuilder(String manufacturer, String name, Integer vramGb, Integer score) {
        int targetScore = requireNonNullElse(score, 10000);

        return Gpu.builder()
            .manufacturer(requireNonNullElse(manufacturer, "NVIDIA"))
            .name(requireNonNullElse(name, "GeForce " + targetScore))
            .vramGb(vramGb)
            .score(targetScore)
            .build();
    }
}
