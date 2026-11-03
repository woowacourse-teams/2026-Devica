package com.wrb.devica.recommendation.domain;

import com.wrb.devica.product.domain.ColorGamut;
import com.wrb.devica.product.domain.CpuTier;
import com.wrb.devica.product.domain.GpuTier;
import com.wrb.devica.product.domain.Os;
import java.util.List;
import java.util.Map;

public record LaptopInterpretation(Map<Os, List<ScoreRange<CpuTier>>> cpu, List<ScoreRange<Integer>> memory,
                                   List<ScoreRange<Integer>> storage, CpuTier macCpuLimit,
                                   List<ScoreRange<ColorGamut>> colorGamut, List<ScoreRange<GpuTier>> gpu) {
}
