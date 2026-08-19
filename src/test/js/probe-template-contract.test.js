const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

test("화면 전환에 참여하는 정적 파일은 같은 캐시 버전을 사용한다", () => {
    const template = fs.readFileSync(
        path.join(__dirname, "../../main/resources/templates/pages/probe/index.html"),
        "utf8"
    );
    const assetPaths = [
        "/css/pages/probe/index.css",
        "/js/probe-policy.js",
        "/js/probe-product-policy.js",
        "/js/probe-config.js",
        "/js/probe-product-view.js",
        "/js/probe-app.js"
    ];
    const versions = assetPaths.map((assetPath) => {
        const escapedPath = assetPath.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
        const match = template.match(new RegExp(`${escapedPath}\\(v='([^']+)'\\)`));
        assert.ok(match, `${assetPath}에 캐시 버전이 있어야 합니다.`);
        return match[1];
    });

    assert.equal(new Set(versions).size, 1);
});

test("기본 권장 사양 제품 보기 버튼은 온보딩 시작 버튼 바로 뒤에 있다", () => {
    const template = fs.readFileSync(
        path.join(__dirname, "../../main/resources/templates/pages/probe/index.html"),
        "utf8"
    );
    const startIndex = template.indexOf('id="start-button"');
    const baselineIndex = template.indexOf('id="baseline-product-button"');

    assert.ok(startIndex >= 0, "온보딩 시작 버튼이 있어야 합니다.");
    assert.ok(baselineIndex > startIndex, "기본 권장 사양 버튼은 온보딩 시작 버튼 뒤에 있어야 합니다.");
    assert.match(template, /id="baseline-product-button"[\s\S]*?기본 권장 사양으로 제품 보기/);
});

test("초기 화면의 제품 수 안내는 승인 제품 세트 수와 일치한다", () => {
    const template = fs.readFileSync(
        path.join(__dirname, "../../main/resources/templates/pages/probe/index.html"),
        "utf8"
    );

    assert.match(template, />20개 중에서</);
});

test("온보딩과 기본 권장 사양 흐름은 하나의 결과 화면과 렌더러를 공유한다", () => {
    const template = fs.readFileSync(
        path.join(__dirname, "../../main/resources/templates/pages/probe/index.html"),
        "utf8"
    );
    const app = fs.readFileSync(
        path.join(__dirname, "../../main/resources/static/js/probe-app.js"),
        "utf8"
    );

    assert.equal((template.match(/id="result-view"/g) || []).length, 1);
    assert.equal((app.match(/function finalizeRecommendation\(/g) || []).length, 1);
    assert.match(app, /function completeRecommendation\(\)[\s\S]*?recalculate\(\);[\s\S]*?finalizeRecommendation\(\);/);
    assert.match(app, /function showBaselineRecommendation\(\)[\s\S]*?createBaselineRecommendation\(\);[\s\S]*?finalizeRecommendation\(\);/);
    assert.doesNotMatch(app, /recommendationSource/);
});

test("Mac 권장 사양 편집은 현재 칩의 RAM 선택지와 호환성 확인을 사용한다", () => {
    const app = fs.readFileSync(
        path.join(__dirname, "../../main/resources/static/js/probe-app.js"),
        "utf8"
    );

    assert.match(app, /recommendationSpecOptions\(os, field, cpuTier\)/);
    assert.match(app, /function ensureCompatibleMacSpecForEdit\(\)/);
    assert.match(app, /window\.confirm\(chipMemoryAdjustmentMessage\(/);
    assert.match(app, /지원하지 않아 `[\s\S]*?변경됩니다\. 그래도 변경하시겠습니까\?/);
    assert.match(app, /resultViewIncludesMac\(value\)[\s\S]*?!ensureCompatibleMacSpecForEdit\(\)/);
});
