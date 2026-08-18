const test = require("node:test");
const assert = require("node:assert/strict");
const productView = require("../../main/resources/static/js/probe-product-view.js");

test("조회된 제품 목록 안에서만 상세 제품을 선택한다", () => {
    const matchedProducts = [
        {id: "matched-1", modelName: "첫 번째 제품"},
        {id: "matched-2", modelName: "두 번째 제품"}
    ];

    assert.equal(productView.findProduct(matchedProducts, "matched-2"), matchedProducts[1]);
    assert.equal(productView.findProduct(matchedProducts, "not-matched"), null);
});

test("목록이나 제품 ID가 없으면 상세 제품을 선택하지 않는다", () => {
    assert.equal(productView.findProduct([], "matched-1"), null);
    assert.equal(productView.findProduct(null, "matched-1"), null);
    assert.equal(productView.findProduct([{id: "matched-1"}], null), null);
});
