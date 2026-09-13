package com.wrb.devica.recommendation;

import com.wrb.devica.product.CpuTier;
import com.wrb.devica.product.LaptopSpec;
import com.wrb.devica.product.Os;
import com.wrb.devica.purpose.UsagePurposeCode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 백엔드 개발 노트북 추천. 아직 답변을 보지 않고 기본안을 그대로 낸다.
 */
@Component
public class LaptopBackendAlgorithm implements RecommendationAlgorithm {

    private static final List<RecommendedSpec> DEFAULT_SPECS = List.of(
        new RecommendedSpec(
            new LaptopSpec(Os.MAC, CpuTier.BASIC, 24, 512),
            Map.of(
                "OS", List.of("Mac 권장안입니다."),
                "CPU_TIER", List.of("Mac 백엔드 개발 기본 CPU 입니다."),
                "MEMORY", List.of("Mac 백엔드 개발 기본 권장 메모리입니다."),
                "STORAGE", List.of("백엔드 개발 기본 저장 공간입니다."))),
        new RecommendedSpec(
            new LaptopSpec(Os.WINDOWS, CpuTier.P_HS, 24, 512),
            Map.of(
                "OS", List.of("Windows 권장안입니다."),
                "CPU_TIER", List.of("Windows 백엔드 개발 기본 CPU 입니다."),
                "MEMORY", List.of("Windows 백엔드 개발 기본 권장 메모리입니다."),
                "STORAGE", List.of("백엔드 개발 기본 저장 공간입니다."))));

    @Override
    public UsagePurposeCode purpose() {
        return UsagePurposeCode.BACKEND_DEVELOPMENT;
    }

    @Override
    public List<RecommendedSpec> recommend(Answers answers) {
        return DEFAULT_SPECS;
    }
}
