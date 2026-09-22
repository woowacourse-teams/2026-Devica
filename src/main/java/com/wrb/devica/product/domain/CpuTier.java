package com.wrb.devica.product.domain;

import java.util.Arrays;
import java.util.Comparator;
import lombok.Getter;

/**
 * 권장 사양이 요구하는 CPU 등급. 한 등급이 제조사를 가리지 않고 같은 점수대를 묶는다 —
 * Intel 과 AMD 를 줄 세울 수 없어도 벤치마크 점수로는 같은 급인지 말할 수 있다.
 * <p>
 * 벤치마크 점수는 상대값이라 등급을 먼저 정의하고 점수대를 그 등급의 정의로 둔다.
 * 등급 사이의 순서도 minScore 가 정한다. 선언 위치는 순서와 무관하다.
 * 같은 OS 안에서 점수가 겹치면 stepUp 이 그 등급을 건너뛴다 — CpuTierTest 가 겹침을 막는다.
 * <p>
 * 표시명에 세대를 넣지 않는다. 특정 모델명을 적으면 다음 세대가 나올 때마다 낡는다.
 * 지금 쓰는 CPU 를 고르는 질문 선택지는 반대로 구체 모델을 예시로 든다 — 그쪽은 사용자가
 * 자기 칩을 알아보는 용도이고 DB 라 배포 없이 갱신된다.
 * <p>
 * 점수대는 잠정값이다 — 시드된 Apple M4(21000), Intel Core Ultra 7 255H(24000) 두 행에만 맞춰 두었다.
 * 표시명은 낡지 않지만 minScore 는 세대마다 다시 맞춰야 한다. 다음 세대 기본 칩이 PRO 의 점수를 넘으면
 * M Pro 조건에 기본 칩이 섞인다.
 */
@Getter
public enum CpuTier {

    BASIC(Os.MAC, "M 칩", 20000),
    PRO(Os.MAC, "M Pro 칩", 30000),
    MAX(Os.MAC, "M Max 칩", 45000),

    U(Os.WINDOWS, "저전력 Core Ultra 5 / Ryzen 5", 15000),
    P_HS(Os.WINDOWS, "고효율 Core Ultra 7 / Ryzen 7", 20000),
    H(Os.WINDOWS, "고성능 Core Ultra 7 / Ryzen 7", 24000),
    HX(Os.WINDOWS, "최고성능 Core Ultra 9 / Ryzen 9", 35000);

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

    public boolean isHigherThan(CpuTier other) {
        return minScore > other.minScore;
    }

    public CpuTier higherOf(CpuTier other) {
        if (minScore >= other.minScore) {
            return this;
        }
        return other;
    }
}
