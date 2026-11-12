package com.wrb.devica.product.dto;

import com.wrb.devica.product.dto.ProductSummaryRow.MatchBasis;
import java.util.ArrayList;
import java.util.List;

public record SpecMatchResponse(
    SpecMatchStatus status,
    List<SpecItemView> exceededSpecs
) {

    public static SpecMatchResponse of(LaptopSearchCondition condition, MatchBasis basis) {
        if (!hasSpecCondition(condition)) {
            return null;
        }

        List<SpecItemView> exceeded = new ArrayList<>();

        if (condition.cpuTier() != null && condition.cpuTier().isExceededBy(basis.cpuScore())) {
            exceeded.add(SpecItemView.CPU);
        }

        if (condition.gpuTier() != null && condition.gpuTier().isExceededBy(basis.gpuScore())) {
            exceeded.add(SpecItemView.GPU);
        }

        if (condition.memoryGb() != null && basis.memoryGb() > condition.memoryGb()) {
            exceeded.add(SpecItemView.MEMORY);
        }

        if (condition.storageGb() != null && basis.storageGb() > condition.storageGb()) {
            exceeded.add(SpecItemView.STORAGE);
        }

        if (condition.colorGamut() != null && basis.colorGamut().compareTo(condition.colorGamut()) > 0) {
            exceeded.add(SpecItemView.COLOR_GAMUT);
        }

        if (exceeded.isEmpty()) {
            return new SpecMatchResponse(SpecMatchStatus.EXACT, List.of());
        }

        return new SpecMatchResponse(SpecMatchStatus.EXCEEDED, List.copyOf(exceeded));
    }

    private static boolean hasSpecCondition(LaptopSearchCondition condition) {
        return condition.cpuTier() != null
            || condition.gpuTier() != null
            || condition.memoryGb() != null
            || condition.storageGb() != null
            || condition.colorGamut() != null;
    }
}
