package com.wrb.devica.recommendation;

import com.wrb.devica.common.BusinessException;
import com.wrb.devica.common.ErrorCode;
import com.wrb.devica.product.LaptopSpec;
import com.wrb.devica.product.Os;
import com.wrb.devica.purpose.UsagePurposeCode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 사용 목적별 노트북 권장 사양 및 근거를 관리한다.
 */
public enum LaptopRecommendation {

    BACKEND_DEVELOPMENT(UsagePurposeCode.BACKEND_DEVELOPMENT, List.of(
        new RecommendedSpec(
            new LaptopSpec(Os.MACOS, "M 칩", 24, 512),
            Map.of(
                "OS", "Mac 권장안입니다.",
                "CPU", "Mac 백엔드 개발 기본 CPU 입니다.",
                "MEMORY", "Mac 백엔드 개발 기본 권장 메모리입니다.",
                "STORAGE", "백엔드 개발 기본 저장 공간입니다.")),
        new RecommendedSpec(
            new LaptopSpec(Os.WINDOWS, "Core Ultra 7 258V / Ryzen AI 7 445급", 24, 512),
            Map.of(
                "OS", "Windows 권장안입니다.",
                "CPU", "Windows 백엔드 개발 기본 CPU 입니다.",
                "MEMORY", "Windows 백엔드 개발 기본 권장 메모리입니다.",
                "STORAGE", "백엔드 개발 기본 저장 공간입니다."))));

    private final UsagePurposeCode purpose;
    private final List<RecommendedSpec> recommendedSpecs;

    LaptopRecommendation(UsagePurposeCode purpose, List<RecommendedSpec> recommendedSpecs) {
        this.purpose = purpose;
        this.recommendedSpecs = recommendedSpecs;
    }

    public static List<RecommendedSpec> findByPurpose(UsagePurposeCode purpose) {
        return Arrays.stream(values())
            .filter(recommendation -> recommendation.purpose == purpose)
            .findFirst()
            .map(recommendation -> recommendation.recommendedSpecs)
            .orElseThrow(() -> new BusinessException(ErrorCode.RECOMMENDATION_NOT_FOUND));
    }
}
