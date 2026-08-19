(function (root, factory) {
    const policy = factory();
    if (typeof module === "object" && module.exports) {
        module.exports = policy;
    }
    root.DevicaProbePolicy = policy;
}(typeof globalThis !== "undefined" ? globalThis : this, function () {
    "use strict";

    const OS = Object.freeze({
        MACOS: "MACOS",
        WINDOWS: "WINDOWS"
    });

    const CPU_TIERS = Object.freeze({
        [OS.MACOS]: Object.freeze(["BASIC", "PRO", "MAX"]),
        [OS.WINDOWS]: Object.freeze(["U", "P_HS", "H", "HX"])
    });

    const CPU_LABELS = Object.freeze({
        [OS.MACOS]: Object.freeze({BASIC: "M 칩", PRO: "M Pro 칩", MAX: "M Max 칩"}),
        [OS.WINDOWS]: Object.freeze({
            U: "Core Ultra 5 235U / Ryzen AI 5 340급",
            P_HS: "Core Ultra 7 258V / Ryzen AI 7 445급",
            H: "Core Ultra 7 255H / Ryzen 7 H 260급",
            HX: "Core Ultra 9 275HX / Ryzen 9 8940HX급"
        })
    });

    const BASELINES = Object.freeze({
        [OS.MACOS]: Object.freeze({os: OS.MACOS, cpuTier: "BASIC", memoryGb: 24, storageGb: 512}),
        [OS.WINDOWS]: Object.freeze({os: OS.WINDOWS, cpuTier: "P_HS", memoryGb: 32, storageGb: 512})
    });

    const MEMORY_OPTIONS = Object.freeze([16, 24, 32, 40, 48, 64]);
    const STORAGE_OPTIONS = Object.freeze([256, 512, 1024]);

    function calculateRecommendation(answers, currentSpec) {
        const languages = arrayAnswer(answers.Q2);
        const javaSelected = languages.includes("JAVA_FAMILY");
        const current = currentSpec || {};
        const activeOs = resolveActiveOs(answers.Q1);
        const tracks = {};

        Object.values(OS).forEach((os) => {
            tracks[os] = calculateTrack(os, answers, current, javaSelected, languages);
        });

        return {activeOs, tracks};
    }

    function createBaselineRecommendation() {
        const tracks = {};
        Object.values(OS).forEach((os) => {
            tracks[os] = {
                os,
                calculatedSpec: cloneSpec(BASELINES[os]),
                reasons: baselineReasons(os)
            };
        });
        return {activeOs: Object.values(OS), tracks};
    }

    function calculateTrack(os, answers, currentSpec, javaSelected, languages) {
        const reasons = baselineReasons(os);

        const memory = calculateMemory(os, answers, javaSelected, reasons.memory);
        const storage = calculateStorage(answers, currentSpec, languages, reasons.storage);
        const cpu = calculateCpu(os, answers, currentSpec, javaSelected, reasons.cpu);

        return {
            os,
            calculatedSpec: {os, cpuTier: cpu, memoryGb: memory, storageGb: storage},
            reasons
        };
    }

    function baselineReasons(os) {
        return {
            cpu: [os === OS.MACOS ? "Mac 백엔드 개발 기본 CPU입니다." : "Windows 백엔드 개발 기본 CPU입니다."],
            memory: [`${os === OS.MACOS ? "Mac" : "Windows"} 기본 권장 메모리에서 시작했습니다.`],
            storage: ["백엔드 개발 기본 저장 공간에서 시작했습니다."],
            os: [os === OS.MACOS ? "Mac 권장안입니다." : "Windows 권장안입니다."]
        };
    }

    function calculateMemory(os, answers, javaSelected, reasons) {
        const baseline = BASELINES[os].memoryGb;
        const fullUp = javaSelected
            || answers.Q3 === "JETBRAINS"
            || answers.Q3 === "MULTIPLE"
            || answers.Q3_1 === "AI_EDITOR"
            || answers.Q4 === "MULTIPLE_SERVICES"
            || answers.Q4 === "DOCKER_MANY"
            || answers.Q5 === "OFTEN";
        const halfUp = !fullUp && answers.Q5 === "SOMETIMES";
        const down = answers.Q4 === "REMOTE" && answers.Q8 === "TWO_YEARS";

        if ((fullUp || halfUp) && down) {
            reasons.push("상향 조건과 하향 조건이 함께 있어 기준값을 유지했습니다.");
            return baseline;
        }
        if (fullUp) {
            reasons.push("개발 도구와 작업 부하를 고려해 메모리를 16GB 높였습니다.");
            if (answers.Q8 === "FIVE_PLUS_YEARS") {
                reasons.push("오래 사용할 계획이 상향 판단을 보강했습니다.");
            }
            return baseline + 16;
        }
        if (halfUp) {
            reasons.push("가끔 발생한 메모리 부족 경험을 반영해 8GB 높였습니다.");
            if (answers.Q8 === "FIVE_PLUS_YEARS") {
                reasons.push("오래 사용할 계획이 상향 판단을 보강했습니다.");
            }
            return baseline + 8;
        }
        if (down) {
            reasons.push("원격 개발과 짧은 사용 계획이 함께 확인되어 8GB 낮췄습니다.");
            return baseline - 8;
        }
        if (answers.Q8 === "FIVE_PLUS_YEARS") {
            reasons.push("오래 사용할 계획은 단독 상향 대신 참고 근거로만 반영했습니다.");
        }
        return baseline;
    }

    function calculateStorage(answers, currentSpec, languages, reasons) {
        const baseline = 512;
        const ssdAtLeast512 = Number(currentSpec.storageGb) >= 512;
        // 현재 SSD가 작다는 걸 아는 경우의 용량 부족 경험은 디스크 크기 탓이라 상향 근거로 쓰지 않는다.
        // 미입력은 "작다"는 근거가 없으므로 사용자의 자기 보고를 그대로 인정한다.
        const smallSsdKnown = currentSpec.storageGb != null && !ssdAtLeast512;
        const up = languages.includes("NODE_TYPESCRIPT")
            || ["MULTIPLE_SERVICES", "DOCKER_MANY", "DOCKER_FEW"].includes(answers.Q4)
            || (!smallSsdKnown && answers.Q6 === "OFTEN");
        const downCount = Number(answers.Q4 === "REMOTE")
            + Number(ssdAtLeast512 && answers.Q6 === "NEVER")
            + Number(answers.Q8 === "TWO_YEARS");
        const down = downCount >= 2;

        if (up && down) {
            reasons.push("상향 조건과 하향 조건이 함께 있어 기준값을 유지했습니다.");
            return baseline;
        }
        if (up) {
            reasons.push("프로젝트와 개발 환경의 저장 공간 사용량을 고려해 1TB를 권장합니다.");
            if (answers.Q8 === "FIVE_PLUS_YEARS") {
                reasons.push("오래 사용할 계획이 상향 판단을 보강했습니다.");
            }
            return 1024;
        }
        if (down) {
            reasons.push("저장 공간 하향 조건이 두 개 이상 확인되어 256GB로 조정했습니다.");
            return 256;
        }
        if (!ssdAtLeast512 && answers.Q6) {
            reasons.push("현재 SSD가 미입력 또는 512GB 상당 미만이라 용량 경험은 수치에 반영하지 않았습니다.");
        }
        if (answers.Q8 === "FIVE_PLUS_YEARS") {
            reasons.push("오래 사용할 계획은 단독 상향 대신 참고 근거로만 반영했습니다.");
        }
        return baseline;
    }

    function calculateCpu(os, answers, currentSpec, javaSelected, reasons) {
        let tier = BASELINES[os].cpuTier;
        if (javaSelected) {
            tier = stepUp(os, tier);
            reasons.push("Java·Kotlin·C# 계열의 빌드 부하를 고려해 한 단계 높였습니다.");
        }
        if (answers.Q7 === "OFTEN") {
            tier = applyExperienceCpuUpgrade(os, tier, currentSpec);
            reasons.push("빌드·테스트 대기 경험을 반영했습니다.");
        }
        if (answers.Q7_1 === "OFTEN") {
            tier = applyExperienceCpuUpgrade(os, tier, currentSpec);
            reasons.push(os === OS.MACOS
                ? "지속 부하와 발열 경험을 반영해 Pro 이상 등급을 검토했습니다."
                : "지속 부하와 발열 경험을 반영해 H·HX 계열을 검토했습니다.");
        }
        return tier;
    }

    function applyExperienceCpuUpgrade(os, currentRecommendedTier, currentSpec) {
        if (currentSpec.os !== os || !currentSpec.cpuTier) {
            return stepUp(os, currentRecommendedTier);
        }
        return maxTier(os, stepUp(os, currentSpec.cpuTier), currentRecommendedTier);
    }

    function resolveActiveOs(q1Answer) {
        if (q1Answer === "MAC") {
            return [OS.MACOS];
        }
        if (q1Answer === "WINDOWS") {
            return [OS.WINDOWS];
        }
        return [OS.MACOS, OS.WINDOWS];
    }

    function stepUp(os, tier) {
        const tiers = CPU_TIERS[os];
        const index = Math.max(0, tiers.indexOf(tier));
        return tiers[Math.min(index + 1, tiers.length - 1)];
    }

    function maxTier(os, first, second) {
        const tiers = CPU_TIERS[os];
        return tiers[Math.max(tiers.indexOf(first), tiers.indexOf(second))];
    }

    function cpuLabel(os, tier) {
        return CPU_LABELS[os][tier] || tier;
    }

    function arrayAnswer(value) {
        return Array.isArray(value) ? value : [];
    }

    function currentSpecSubmissionStatus(currentSpec) {
        const inputCount = Object.values(currentSpec || {}).filter((value) => value != null).length;
        if (inputCount === 0) {
            return "ALL_SKIPPED";
        }
        return inputCount === 4 ? "COMPLETE" : "PARTIAL";
    }

    function cloneSpec(spec) {
        return {...spec};
    }

    return Object.freeze({
        OS,
        CPU_TIERS,
        CPU_LABELS,
        BASELINES,
        MEMORY_OPTIONS,
        STORAGE_OPTIONS,
        calculateRecommendation,
        createBaselineRecommendation,
        resolveActiveOs,
        stepUp,
        maxTier,
        cpuLabel,
        currentSpecSubmissionStatus,
        cloneSpec
    });
}));
