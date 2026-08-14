const test = require("node:test");
const assert = require("node:assert/strict");
const productPolicy = require("../../main/resources/static/js/probe-product-policy.js");
const recommendationPolicy = require("../../main/resources/static/js/probe-policy.js");

const CPU_TIERS = Object.freeze({
    MACOS: Object.freeze(["BASIC", "PRO", "MAX"]),
    WINDOWS: Object.freeze(["U", "P_HS", "H", "HX"])
});

const approvedConfig = loadApprovedConfig();

test("실제 승인 제품 세트는 16개 계약과 불변성을 만족한다", () => {
    assert.equal(approvedConfig.productSetVersion, "product-set-2026-08-14-v0.1");
    assert.equal(approvedConfig.productSetApproved, true);
    assert.deepEqual(approvedConfig.productSetValidation, {valid: true, errors: []});
    assert.equal(approvedConfig.products.length, 16);
    assert.equal(Object.isFrozen(approvedConfig.products), true);
    assert.equal(approvedConfig.products.every(Object.isFrozen), true);

    const counts = approvedConfig.products.reduce((result, product) => {
        result.os[product.os] = (result.os[product.os] || 0) + 1;
        result.brand[product.brand] = (result.brand[product.brand] || 0) + 1;
        return result;
    }, {os: {}, brand: {}});
    assert.deepEqual(counts.os, {MACOS: 8, WINDOWS: 8});
    assert.deepEqual(counts.brand, {Apple: 8, Samsung: 2, LG: 2, Lenovo: 2, ASUS: 2});
});

test("실제 승인 제품은 쿠팡 일반가와 승인된 수정 가격을 사용한다", () => {
    approvedConfig.products.forEach((product) => {
        const url = new URL(product.purchaseUrl);
        assert.equal(product.sourceName, "쿠팡");
        assert.equal(product.priceType, "GENERAL");
        assert.equal(product.checkedAt, "2026-08-14");
        assert.equal(url.hostname, "www.coupang.com");
        assert.ok(url.searchParams.get("itemId"));
        assert.ok(url.searchParams.get("vendorItemId"));
        assert.deepEqual([...url.searchParams.keys()].sort(), ["itemId", "vendorItemId"]);
    });

    const changed = approvedConfig.products.find((product) => product.id === "mac-pro14-m5-24-1t");
    assert.equal(changed.priceKrw, 3_321_990);
    assert.equal(
        approvedConfig.products.find((product) => product.id === "win-lg-grampro16-258v").cpuTier,
        "P_HS"
    );
    assert.equal(
        approvedConfig.products.find((product) => product.id === "win-asus-zenbook14-ai7").cpuTier,
        "P_HS"
    );
});

test("실제 승인 제품 검색은 Windows 48GB 조건을 완화하지 않는다", () => {
    const matches = productPolicy.findMatches(
        approvedConfig.products,
        {os: "WINDOWS", cpuTier: "P_HS", memoryGb: 48, storageGb: 512},
        CPU_TIERS
    );
    assert.deepEqual(matches, []);
});

test("Mac과 Windows 통합 결과는 전체 일반가 오름차순으로 정렬한다", () => {
    const mac = productPolicy.findMatches(
        approvedConfig.products,
        {os: "MACOS", cpuTier: "BASIC", memoryGb: 24, storageGb: 512},
        CPU_TIERS
    );
    const windows = productPolicy.findMatches(
        approvedConfig.products,
        {os: "WINDOWS", cpuTier: "P_HS", memoryGb: 32, storageGb: 512},
        CPU_TIERS
    );

    const combined = productPolicy.sortByPrice([...mac, ...windows]);

    assert.equal(combined.length, 16);
    assert.equal(combined[0].id, "win-asus-zenbook14-ai7");
    assert.deepEqual(
        combined.map((product) => product.priceKrw),
        [...combined.map((product) => product.priceKrw)].sort((left, right) => left - right)
    );
});

test("가격 정렬은 입력 배열을 변경하지 않는다", () => {
    const high = sampleProduct("WINDOWS", "Samsung", "H", 1, 2_900_000);
    const low = sampleProduct("WINDOWS", "LG", "H", 2, 1_900_000);
    const products = [high, low];

    assert.deepEqual(productPolicy.sortByPrice(products).map((product) => product.id), [low.id, high.id]);
    assert.deepEqual(products.map((product) => product.id), [high.id, low.id]);
});

test("미승인 제품 세트는 빈 배열만 허용한다", () => {
    assert.equal(productPolicy.validateCatalog({products: [], approved: false, cpuTiers: CPU_TIERS}).valid, true);

    const invalid = productPolicy.validateCatalog({
        products: [sampleProduct("MACOS", "Apple", "BASIC", 1)],
        approved: false,
        cpuTiers: CPU_TIERS
    });
    assert.equal(invalid.valid, false);
    assert.match(invalid.errors.join("\n"), /빈 배열/);
});

test("승인 제품 세트는 Mac 8개와 Windows 브랜드별 2개를 요구한다", () => {
    const products = [];
    for (let index = 0; index < 8; index += 1) {
        const product = sampleProduct(
            "MACOS",
            "Apple",
            index === 7 ? "MAX" : index >= 5 ? "PRO" : "BASIC",
            index
        );
        product.memoryGb = index >= 5 ? 48 : 32;
        products.push(product);
    }
    ["Samsung", "LG", "Lenovo", "ASUS"].forEach((brand, brandIndex) => {
        products.push(sampleProduct("WINDOWS", brand, "P_HS", 8 + brandIndex * 2));
        products.push(sampleProduct("WINDOWS", brand, "HX", 9 + brandIndex * 2));
    });

    const validation = productPolicy.validateCatalog({products, approved: true, cpuTiers: CPU_TIERS});
    assert.deepEqual(validation, {valid: true, errors: []});
});

test("쿠팡 일반가와 정확한 옵션 URL만 허용한다", () => {
    const product = sampleProduct("MACOS", "Apple", "BASIC", 1);
    product.sourceName = "Apple";
    product.priceType = "COUPON";
    product.purchaseUrl = "https://example.com/item?itemId=1";

    const validation = productPolicy.validateCatalog({
        products: Array.from({length: 16}, (_, index) => ({...product, id: `id-${index}`})),
        approved: true,
        cpuTiers: CPU_TIERS
    });
    assert.equal(validation.valid, false);
    assert.match(validation.errors.join("\n"), /GENERAL/);
    assert.match(validation.errors.join("\n"), /sourceName은 쿠팡/);
    assert.match(validation.errors.join("\n"), /HTTPS 쿠팡 URL/);
});

test("최상위 CPU 등급을 검색할 제품이 없으면 승인할 수 없다", () => {
    const products = [];
    for (let index = 0; index < 8; index += 1) {
        products.push(sampleProduct("MACOS", "Apple", index < 2 ? "PRO" : "BASIC", index));
    }
    ["Samsung", "LG", "Lenovo", "ASUS"].forEach((brand, brandIndex) => {
        products.push(sampleProduct("WINDOWS", brand, "P_HS", 8 + brandIndex * 2));
        products.push(sampleProduct("WINDOWS", brand, "H", 9 + brandIndex * 2));
    });

    const validation = productPolicy.validateCatalog({products, approved: true, cpuTiers: CPU_TIERS});
    assert.equal(validation.valid, false);
    assert.match(validation.errors.join("\n"), /Mac Max 상향/);
    assert.match(validation.errors.join("\n"), /Windows HX 상향/);
});

test("제품 검색은 사양을 완화하지 않고 일반가 오름차순으로 정렬한다", () => {
    const spec = {os: "WINDOWS", cpuTier: "H", memoryGb: 32, storageGb: 1024};
    const low = sampleProduct("WINDOWS", "Samsung", "H", 1, 1_900_000);
    const high = sampleProduct("WINDOWS", "LG", "HX", 2, 2_900_000);
    const belowCpu = sampleProduct("WINDOWS", "Lenovo", "P_HS", 3, 1_000_000);
    const belowMemory = {...sampleProduct("WINDOWS", "ASUS", "HX", 4, 900_000), memoryGb: 16};

    assert.deepEqual(
        productPolicy.findMatches([high, belowCpu, low, belowMemory], spec, CPU_TIERS).map((product) => product.id),
        [low.id, high.id]
    );
});

function sampleProduct(os, brand, cpuTier, index, priceKrw = 2_000_000 + index) {
    return {
        id: `${os.toLowerCase()}-${brand.toLowerCase()}-${index}`,
        brand,
        modelName: `모델 ${index}`,
        modelCode: `CODE-${index}`,
        os,
        cpuManufacturer: os === "MACOS" ? "Apple" : "Intel",
        cpuModelName: os === "MACOS" ? "Apple M5" : "Intel Core Ultra 7",
        cpuTier,
        memoryGb: 32,
        storageGb: 1024,
        priceKrw,
        priceType: "GENERAL",
        sourceName: "쿠팡",
        purchaseUrl: `https://www.coupang.com/vp/products/${1000 + index}?itemId=${2000 + index}&vendorItemId=${3000 + index}`,
        checkedAt: "2026-08-14",
        active: true
    };
}

function loadApprovedConfig() {
    const previousWindow = global.window;
    global.window = {
        DevicaProbePolicy: recommendationPolicy,
        DevicaProductPolicy: productPolicy
    };
    require("../../main/resources/static/js/probe-config.js");
    const config = global.window.DEVICA_PROBE_CONFIG;
    global.window = previousWindow;
    return config;
}
