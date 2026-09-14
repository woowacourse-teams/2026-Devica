package com.wrb.devica.product;

import java.util.Arrays;
import java.util.Comparator;
import lombok.Getter;

/**
 * 권장 사양이 요구하는 CPU 등급. 한 등급이 제조사를 가리지 않고 같은 점수대를 묶는다 —
 * Intel 과 AMD 를 줄 세울 수 없어도 벤치마크 점수로는 같은 급인지 말할 수 있다.
 * <p>
 * 벤치마크 점수는 상대값이라 등급을 먼저 정의하고 점수대를 그 등급의 정의로 둔다.
 * 등급 사이의 순서도 minScore 가 정한다. 선언 위치는 순서와 무관하다.
 * <p>
 * 점수대는 잠정값이다 — 시드된 Apple M4(21000), Intel Core Ultra 7 255H(24000) 두 행에만 맞춰 두었다.
 */
@Getter
public enum CpuTier {

    BASIC(Os.MAC, "M 칩", 20000),
    PRO(Os.MAC, "M Pro 칩", 30000),
    MAX(Os.MAC, "M Max 칩", 45000),

    U(Os.WINDOWS, "Core Ultra 5 235U / Ryzen AI 5 340급", 15000),
    P_HS(Os.WINDOWS, "Core Ultra 7 258V / Ryzen AI 7 445급", 20000),
    H(Os.WINDOWS, "Core Ultra 7 255H / Ryzen 7 H 260급", 24000),
    HX(Os.WINDOWS, "Core Ultra 9 275HX / Ryzen 9 8940HX급", 35000);

    private final Os os;
    private final String displayName;
    private final int minScore;

    CpuTier(Os os, String displayName, int minScore) {
        this.os = os;
        this.displayName = displayName;
        this.minScore = minScore;
    }

    public CpuTier stepUp() {
        return Arrays.stream(values())
            .filter(tier -> tier.os == os && tier.minScore > minScore)
            .min(Comparator.comparingInt(CpuTier::getMinScore))
            .orElse(this);
    }

    public CpuTier higherOf(CpuTier other) {
        if (minScore >= other.minScore) {
            return this;
        }
        return other;
    }
}
