const test = require("node:test");
const assert = require("node:assert/strict");
const policy = require("../../main/resources/static/js/probe-policy.js");

test("OS 미정이면 Mac과 Windows 권장안을 모두 계산한다", () => {
    const result = policy.calculateRecommendation({Q1: "UNDECIDED"}, {});

    assert.deepEqual(result.activeOs, [policy.OS.MACOS, policy.OS.WINDOWS]);
    assert.equal(result.tracks.MACOS.calculatedSpec.memoryGb, 24);
    assert.equal(result.tracks.WINDOWS.calculatedSpec.memoryGb, 32);
});

test("기본 권장안은 온보딩 계산 없이 동일한 기준 사양을 두 OS에 적용한다", () => {
    const result = policy.createBaselineRecommendation();

    assert.deepEqual(result.activeOs, [policy.OS.MACOS, policy.OS.WINDOWS]);
    assert.deepEqual(result.tracks.MACOS.calculatedSpec, policy.BASELINES.MACOS);
    assert.deepEqual(result.tracks.WINDOWS.calculatedSpec, policy.BASELINES.WINDOWS);
    assert.notEqual(result.tracks.MACOS.calculatedSpec, policy.BASELINES.MACOS);
    assert.notEqual(result.tracks.WINDOWS.calculatedSpec, policy.BASELINES.WINDOWS);
});

test("Java 계열은 Mac 메모리를 48GB로, CPU를 한 단계 상향한다", () => {
    const result = policy.calculateRecommendation({Q1: "MAC", Q2: ["JAVA_FAMILY"]}, {});

    assert.equal(result.tracks.MACOS.calculatedSpec.memoryGb, 48);
    assert.equal(result.tracks.MACOS.calculatedSpec.cpuTier, "PRO");
});

test("Mac Q5의 자주와 가끔은 각각 48GB와 32GB를 권장한다", () => {
    const often = policy.calculateRecommendation({Q1: "MAC", Q2: [], Q5: "OFTEN"}, {});
    const sometimes = policy.calculateRecommendation({Q1: "MAC", Q2: [], Q5: "SOMETIMES"}, {});

    assert.equal(often.tracks.MACOS.calculatedSpec.memoryGb, 48);
    assert.equal(often.tracks.MACOS.calculatedSpec.cpuTier, "PRO");
    assert.equal(sometimes.tracks.MACOS.calculatedSpec.memoryGb, 32);
    assert.equal(sometimes.tracks.MACOS.calculatedSpec.cpuTier, "BASIC");
});

test("Mac 16GB와 32GB 권장 결과는 CPU 상향 조건이 있어도 M 칩으로 맞춘다", () => {
    const sixteen = policy.calculateRecommendation({
        Q1: "MAC",
        Q4: "REMOTE",
        Q7: "OFTEN",
        Q8: "TWO_YEARS"
    }, {});
    const thirtyTwo = policy.calculateRecommendation({
        Q1: "MAC",
        Q5: "SOMETIMES",
        Q7: "OFTEN"
    }, {});

    assert.deepEqual(sixteen.tracks.MACOS.calculatedSpec, {
        os: policy.OS.MACOS,
        cpuTier: "BASIC",
        memoryGb: 16,
        storageGb: 512
    });
    assert.deepEqual(thirtyTwo.tracks.MACOS.calculatedSpec, {
        os: policy.OS.MACOS,
        cpuTier: "BASIC",
        memoryGb: 32,
        storageGb: 512
    });
    assert.match(sixteen.tracks.MACOS.reasons.cpu.join(" "), /16GB RAM 지원 조합/);
    assert.match(thirtyTwo.tracks.MACOS.reasons.cpu.join(" "), /32GB RAM 지원 조합/);
});

test("Mac 24GB 권장 결과는 기존 CPU 판정을 유지한다", () => {
    const basic = policy.calculateRecommendation({Q1: "MAC"}, {});
    const pro = policy.calculateRecommendation({Q1: "MAC", Q7: "OFTEN"}, {});

    assert.equal(basic.tracks.MACOS.calculatedSpec.memoryGb, 24);
    assert.equal(basic.tracks.MACOS.calculatedSpec.cpuTier, "BASIC");
    assert.equal(pro.tracks.MACOS.calculatedSpec.memoryGb, 24);
    assert.equal(pro.tracks.MACOS.calculatedSpec.cpuTier, "PRO");
});

test("Mac 권장 메모리는 허용된 네 값 안에서만 계산한다", () => {
    const results = [
        policy.calculateRecommendation({Q1: "MAC"}, {}),
        policy.calculateRecommendation({Q1: "MAC", Q5: "SOMETIMES"}, {}),
        policy.calculateRecommendation({Q1: "MAC", Q5: "OFTEN"}, {}),
        policy.calculateRecommendation({Q1: "MAC", Q4: "REMOTE", Q8: "TWO_YEARS"}, {})
    ];

    assert.deepEqual(
        results.map((result) => result.tracks.MACOS.calculatedSpec.memoryGb),
        [24, 32, 48, 16]
    );
});

test("Windows 권장 메모리 계산은 기존 상향과 하향 값을 유지한다", () => {
    const fullUp = policy.calculateRecommendation({Q1: "WINDOWS", Q5: "OFTEN"}, {});
    const halfUp = policy.calculateRecommendation({Q1: "WINDOWS", Q5: "SOMETIMES"}, {});
    const down = policy.calculateRecommendation({Q1: "WINDOWS", Q4: "REMOTE", Q8: "TWO_YEARS"}, {});

    assert.equal(fullUp.tracks.WINDOWS.calculatedSpec.memoryGb, 48);
    assert.equal(halfUp.tracks.WINDOWS.calculatedSpec.memoryGb, 40);
    assert.equal(down.tracks.WINDOWS.calculatedSpec.memoryGb, 24);
});

test("메모리 상향과 하향 조건이 동시에 성립하면 기준값으로 상쇄한다", () => {
    const result = policy.calculateRecommendation({
        Q1: "WINDOWS",
        Q2: ["JAVA_FAMILY"],
        Q4: "REMOTE",
        Q8: "TWO_YEARS"
    }, {});

    assert.equal(result.tracks.WINDOWS.calculatedSpec.memoryGb, 32);
});

test("SSD 512GB 상당 이상과 Q6 답변을 저장 공간 판정에 사용한다", () => {
    const up = policy.calculateRecommendation({Q1: "MAC", Q2: [], Q6: "OFTEN"}, {storageGb: 512});
    const unchanged = policy.calculateRecommendation({Q1: "MAC", Q2: [], Q6: "OFTEN"}, {storageGb: 256});

    assert.equal(up.tracks.MACOS.calculatedSpec.storageGb, 1024);
    assert.equal(unchanged.tracks.MACOS.calculatedSpec.storageGb, 512);
});

test("현재 SSD 미입력이면 용량 부족 경험을 상향에 반영하되 하향에는 쓰지 않는다", () => {
    const up = policy.calculateRecommendation({Q1: "MAC", Q2: [], Q6: "OFTEN"}, {});
    const small = policy.calculateRecommendation({Q1: "MAC", Q2: [], Q6: "OFTEN"}, {storageGb: 256});
    const down = policy.calculateRecommendation({Q1: "MAC", Q2: [], Q4: "REMOTE", Q6: "NEVER"}, {});

    assert.equal(up.tracks.MACOS.calculatedSpec.storageGb, 1024);
    assert.equal(small.tracks.MACOS.calculatedSpec.storageGb, 512);
    assert.equal(down.tracks.MACOS.calculatedSpec.storageGb, 512);
});

test("저장 공간 상향과 하향이 함께 성립하면 512GB로 상쇄한다", () => {
    const result = policy.calculateRecommendation({
        Q1: "MAC",
        Q2: ["NODE_TYPESCRIPT"],
        Q4: "REMOTE",
        Q6: "NEVER",
        Q8: "TWO_YEARS"
    }, {storageGb: 512});

    assert.equal(result.tracks.MACOS.calculatedSpec.storageGb, 512);
});

test("Mac 권장 저장 공간은 512GB 미만으로 낮추지 않고 Windows 하향은 유지한다", () => {
    const answers = {Q4: "REMOTE", Q6: "NEVER", Q8: "TWO_YEARS"};
    const currentSpec = {storageGb: 512};
    const mac = policy.calculateRecommendation({Q1: "MAC", ...answers}, currentSpec);
    const windows = policy.calculateRecommendation({Q1: "WINDOWS", ...answers}, currentSpec);

    assert.equal(mac.tracks.MACOS.calculatedSpec.storageGb, 512);
    assert.equal(windows.tracks.WINDOWS.calculatedSpec.storageGb, 256);
});

test("현재 CPU는 같은 OS에만 반영하고 최종 권장 CPU의 하한을 지킨다", () => {
    const result = policy.calculateRecommendation({
        Q1: "UNDECIDED",
        Q2: ["JAVA_FAMILY"],
        Q7: "OFTEN"
    }, {os: policy.OS.WINDOWS, cpuTier: "U"});

    assert.equal(result.tracks.WINDOWS.calculatedSpec.cpuTier, "H");
    assert.equal(result.tracks.MACOS.calculatedSpec.cpuTier, "PRO");
});

test("Mac 온보딩 CPU 계산은 Max 조건에서도 Pro로 제한한다", () => {
    const result = policy.calculateRecommendation({
        Q1: "MAC",
        Q2: ["JAVA_FAMILY"],
        Q7: "OFTEN"
    }, {});

    assert.equal(result.tracks.MACOS.calculatedSpec.cpuTier, "PRO");
    assert.match(result.tracks.MACOS.reasons.cpu.join(" "), /예산 범위/);
});

test("CPU에 영향을 주는 모든 온보딩 조합에서 Mac 권장 결과는 Max가 아니다", () => {
    const languageAnswers = [[], ["JAVA_FAMILY"]];
    const experienceAnswers = [undefined, "OFTEN"];
    const currentSpecs = [
        {},
        {os: policy.OS.MACOS, cpuTier: "BASIC"},
        {os: policy.OS.MACOS, cpuTier: "PRO"},
        {os: policy.OS.MACOS, cpuTier: "MAX"},
        {os: policy.OS.WINDOWS, cpuTier: "HX"}
    ];

    ["MAC", "UNDECIDED"].forEach((q1) => {
        languageAnswers.forEach((q2) => {
            experienceAnswers.forEach((q7) => {
                experienceAnswers.forEach((q7_1) => {
                    currentSpecs.forEach((currentSpec) => {
                        const result = policy.calculateRecommendation({Q1: q1, Q2: q2, Q7: q7, Q7_1: q7_1}, currentSpec);
                        assert.ok(
                            ["BASIC", "PRO"].includes(result.tracks.MACOS.calculatedSpec.cpuTier),
                            `Mac 권장 CPU가 Max이면 안 됩니다: ${JSON.stringify({q1, q2, q7, q7_1, currentSpec})}`
                        );
                    });
                });
            });
        });
    });
});

test("Mac 권장 사양 선택지는 칩별 지원 RAM과 저장 공간 범위를 사용한다", () => {
    assert.deepEqual(policy.recommendationSpecOptions(policy.OS.MACOS, "cpuTier"), ["BASIC", "PRO"]);
    assert.deepEqual(policy.recommendationSpecOptions(policy.OS.MACOS, "memoryGb"), [16, 24, 32, 48]);
    assert.deepEqual(policy.recommendationSpecOptions(policy.OS.MACOS, "memoryGb", "BASIC"), [16, 24, 32]);
    assert.deepEqual(policy.recommendationSpecOptions(policy.OS.MACOS, "memoryGb", "PRO"), [24, 48]);
    assert.deepEqual(policy.recommendationSpecOptions(policy.OS.MACOS, "storageGb"), [512, 1024]);
    assert.equal(Object.isFrozen(policy.MAC_RECOMMENDATION_SPEC_OPTIONS), true);
    assert.equal(Object.isFrozen(policy.MAC_RECOMMENDATION_MEMORY_OPTIONS_BY_CPU), true);
    assert.equal(Object.isFrozen(policy.MAC_RECOMMENDATION_MEMORY_OPTIONS_BY_CPU.BASIC), true);
    assert.equal(Object.isFrozen(policy.MAC_RECOMMENDATION_MEMORY_OPTIONS_BY_CPU.PRO), true);
});

test("Mac 권장 사양의 비호환 칩과 RAM은 승인된 값으로 보정한다", () => {
    assert.equal(policy.adjustRecommendationMemoryForCpu(policy.OS.MACOS, "BASIC", 48), 32);
    assert.equal(policy.adjustRecommendationMemoryForCpu(policy.OS.MACOS, "PRO", 16), 24);
    assert.equal(policy.adjustRecommendationMemoryForCpu(policy.OS.MACOS, "PRO", 32), 24);

    assert.equal(policy.adjustRecommendationMemoryForCpu(policy.OS.MACOS, "BASIC", 16), 16);
    assert.equal(policy.adjustRecommendationMemoryForCpu(policy.OS.MACOS, "BASIC", 24), 24);
    assert.equal(policy.adjustRecommendationMemoryForCpu(policy.OS.MACOS, "BASIC", 32), 32);
    assert.equal(policy.adjustRecommendationMemoryForCpu(policy.OS.MACOS, "PRO", 24), 24);
    assert.equal(policy.adjustRecommendationMemoryForCpu(policy.OS.MACOS, "PRO", 48), 48);
    assert.equal(policy.adjustRecommendationMemoryForCpu(policy.OS.WINDOWS, "HX", 64), 64);
});

test("Mac 온보딩 CPU는 최종 RAM을 지원하는 칩으로 정합화한다", () => {
    assert.equal(policy.alignRecommendationCpuToMemory(policy.OS.MACOS, "PRO", 16), "BASIC");
    assert.equal(policy.alignRecommendationCpuToMemory(policy.OS.MACOS, "BASIC", 24), "BASIC");
    assert.equal(policy.alignRecommendationCpuToMemory(policy.OS.MACOS, "PRO", 24), "PRO");
    assert.equal(policy.alignRecommendationCpuToMemory(policy.OS.MACOS, "PRO", 32), "BASIC");
    assert.equal(policy.alignRecommendationCpuToMemory(policy.OS.MACOS, "BASIC", 48), "PRO");
    assert.equal(policy.alignRecommendationCpuToMemory(policy.OS.WINDOWS, "P_HS", 48), "P_HS");
});

test("Mac 온보딩의 메모리·CPU 상향 조합은 항상 지원 가능한 결과를 만든다", () => {
    const memoryAnswers = [
        {},
        {Q4: "REMOTE", Q8: "TWO_YEARS"},
        {Q5: "SOMETIMES"},
        {Q5: "OFTEN"}
    ];
    const cpuAnswers = [{}, {Q7: "OFTEN"}, {Q7_1: "OFTEN"}];

    memoryAnswers.forEach((memoryAnswer) => {
        cpuAnswers.forEach((cpuAnswer) => {
            const result = policy.calculateRecommendation({
                Q1: "MAC",
                ...memoryAnswer,
                ...cpuAnswer
            }, {});
            const spec = result.tracks.MACOS.calculatedSpec;
            const supportedMemory = policy.recommendationSpecOptions(
                policy.OS.MACOS,
                "memoryGb",
                spec.cpuTier
            );

            assert.ok(
                supportedMemory.includes(spec.memoryGb),
                `지원하지 않는 Mac 조합입니다: ${JSON.stringify(spec)}`
            );
        });
    });
});

test("Windows 권장 사양 선택지는 기존 값을 유지한다", () => {
    assert.deepEqual(policy.recommendationSpecOptions(policy.OS.WINDOWS, "cpuTier"), ["U", "P_HS", "H", "HX"]);
    assert.deepEqual(policy.recommendationSpecOptions(policy.OS.WINDOWS, "memoryGb"), [16, 24, 32, 40, 48, 64]);
    assert.deepEqual(policy.recommendationSpecOptions(policy.OS.WINDOWS, "storageGb"), [256, 512, 1024]);
});

test("현재 사양 입력과 제품 등급 비교용 Mac CPU에는 Max를 유지한다", () => {
    assert.deepEqual(policy.CPU_TIERS.MACOS, ["BASIC", "PRO", "MAX"]);
});

test("CPU는 최상위 등급에서 더 올라가지 않는다", () => {
    const result = policy.calculateRecommendation({
        Q1: "WINDOWS",
        Q2: ["JAVA_FAMILY"],
        Q7: "OFTEN",
        Q7_1: "OFTEN"
    }, {});

    assert.equal(result.tracks.WINDOWS.calculatedSpec.cpuTier, "HX");
});

test("현재 사양 입력 상태는 실제 값이 있는 필드만 계산한다", () => {
    assert.equal(policy.currentSpecSubmissionStatus({}), "ALL_SKIPPED");
    assert.equal(policy.currentSpecSubmissionStatus({os: undefined, cpuTier: undefined}), "ALL_SKIPPED");
    assert.equal(policy.currentSpecSubmissionStatus({memoryGb: 16}), "PARTIAL");
    assert.equal(policy.currentSpecSubmissionStatus({
        os: "MACOS",
        cpuTier: "BASIC",
        memoryGb: 24,
        storageGb: 512
    }), "COMPLETE");
});
