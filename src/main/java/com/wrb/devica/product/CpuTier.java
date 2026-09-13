package com.wrb.devica.product;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;

/**
 * 권장 사양이 요구하는 CPU 등급. OS 안에서 선언 순서가 곧 등급 순서다.
 * <p>
 * 벤치마크 점수는 절대 기준이 아니라 상대값이라, 등급을 먼저 정의하고 점수대를 그 등급의 정의로 둔다.
 * cpu.score 가 칩의 측정값이라면 minScore 는 우리 시스템의 분류 기준이고, 제품 검색의 하한이 된다.
 * <p>
 * 점수대는 잠정값이다 — 시드된 Apple M4(21000), Intel Core Ultra 7 255H(24000) 두 행에만 맞춰 두었다.
 * 등급 값이 OS 별로 겹치지 않아 한 enum 에 담는다.
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

    public static List<CpuTier> ladderOf(Os os) {
        return Arrays.stream(values())
            .filter(tier -> tier.os == os)
            .toList();
    }

    public CpuTier stepUp() {
        List<CpuTier> ladder = ladderOf(os);
        int next = ladder.indexOf(this) + 1;
        return next < ladder.size() ? ladder.get(next) : this;
    }

    public CpuTier higherOf(CpuTier other) {
        if (other.os != os) {
            throw new IllegalArgumentException("OS 가 다른 등급끼리는 비교할 수 없습니다: " + this + ", " + other);
        }
        return compareTo(other) >= 0 ? this : other;
    }
}
