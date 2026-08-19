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

test("Java 계열은 메모리 16GB와 CPU 한 단계를 상향한다", () => {
    const result = policy.calculateRecommendation({Q1: "MAC", Q2: ["JAVA_FAMILY"]}, {});

    assert.equal(result.tracks.MACOS.calculatedSpec.memoryGb, 40);
    assert.equal(result.tracks.MACOS.calculatedSpec.cpuTier, "PRO");
});

test("Q5의 자주와 가끔은 각각 16GB와 8GB를 상향한다", () => {
    const often = policy.calculateRecommendation({Q1: "MAC", Q2: [], Q5: "OFTEN"}, {});
    const sometimes = policy.calculateRecommendation({Q1: "MAC", Q2: [], Q5: "SOMETIMES"}, {});

    assert.equal(often.tracks.MACOS.calculatedSpec.memoryGb, 40);
    assert.equal(sometimes.tracks.MACOS.calculatedSpec.memoryGb, 32);
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

test("현재 CPU는 같은 OS에만 반영하고 최종 권장 CPU의 하한을 지킨다", () => {
    const result = policy.calculateRecommendation({
        Q1: "UNDECIDED",
        Q2: ["JAVA_FAMILY"],
        Q7: "OFTEN"
    }, {os: policy.OS.WINDOWS, cpuTier: "U"});

    assert.equal(result.tracks.WINDOWS.calculatedSpec.cpuTier, "H");
    assert.equal(result.tracks.MACOS.calculatedSpec.cpuTier, "MAX");
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
