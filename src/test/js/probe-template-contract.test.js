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
