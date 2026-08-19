const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const productPolicy = require("../../main/resources/static/js/probe-product-policy.js");
const recommendationPolicy = require("../../main/resources/static/js/probe-policy.js");

const CPU_TIERS = Object.freeze({
    MACOS: Object.freeze(["BASIC", "PRO", "MAX"]),
    WINDOWS: Object.freeze(["U", "P_HS", "H", "HX"])
});

const approvedConfig = loadApprovedConfig();
const NEW_PRODUCT_EXPECTATIONS = Object.freeze({
    "mac-air13-m5-16-512": Object.freeze({
        modelCode: "MDH74KH/A",
        cpuTier: "BASIC",
        memoryGb: 16,
        storageGb: 512,
        priceKrw: 2_080_500,
        purchaseUrl: "https://www.coupang.com/vp/products/9410641464?itemId=27961674147&vendorItemId=94919713475"
    }),
    "mac-air15-m5-16-512": Object.freeze({
        modelCode: "MDVD4KH/A",
        cpuTier: "BASIC",
        memoryGb: 16,
        storageGb: 512,
        priceKrw: 2_330_640,
        purchaseUrl: "https://www.coupang.com/vp/products/9410641499?itemId=27961674328&vendorItemId=94919713458"
    }),
    "mac-pro14-m5-16-1t": Object.freeze({
        modelCode: "MDE14KH/A",
        cpuTier: "BASIC",
        memoryGb: 16,
        storageGb: 1024,
        priceKrw: 3_047_370,
        purchaseUrl: "https://www.coupang.com/vp/products/9140128274?itemId=26906195808&vendorItemId=93875613386"
    }),
    "mac-pro14-m5-32-1t": Object.freeze({
        modelCode: "Z1KN0001E",
        cpuTier: "BASIC",
        memoryGb: 32,
        storageGb: 1024,
        priceKrw: 3_970_000,
        purchaseUrl: "https://www.coupang.com/vp/products/9140128274?itemId=27033962079&vendorItemId=94002473487"
    }),
    "mac-pro16-m5pro-24-1t": Object.freeze({
        modelCode: "MGE44KH/A",
        cpuTier: "PRO",
        memoryGb: 24,
        storageGb: 1024,
        priceKrw: 4_491_000,
        purchaseUrl: "https://www.coupang.com/vp/products/9410700446?itemId=27961799434&vendorItemId=94919837914"
    })
});

test("실제 승인 제품 세트는 20개 계약과 불변성을 만족한다", () => {
    assert.equal(approvedConfig.productSetVersion, "product-set-2026-08-19-v0.2");
    assert.equal(approvedConfig.productSetApproved, true);
    assert.deepEqual(approvedConfig.productSetValidation, {valid: true, errors: []});
    assert.equal(approvedConfig.products.length, 20);
    assert.equal(Object.isFrozen(approvedConfig.products), true);
    assert.equal(approvedConfig.products.every(Object.isFrozen), true);

    const counts = approvedConfig.products.reduce((result, product) => {
        result.os[product.os] = (result.os[product.os] || 0) + 1;
        result.brand[product.brand] = (result.brand[product.brand] || 0) + 1;
        return result;
    }, {os: {}, brand: {}});
    assert.deepEqual(counts.os, {MACOS: 12, WINDOWS: 8});
    assert.deepEqual(counts.brand, {Apple: 12, Samsung: 2, LG: 2, Lenovo: 2, ASUS: 2});
});

test("실제 승인 제품은 쿠팡 일반가와 제품별 확인일을 사용한다", () => {
    const newProductIds = new Set(Object.keys(NEW_PRODUCT_EXPECTATIONS));
    approvedConfig.products.forEach((product) => {
        const url = new URL(product.purchaseUrl);
        assert.equal(product.sourceName, "쿠팡");
        assert.equal(product.priceType, "GENERAL");
        assert.equal(product.checkedAt, newProductIds.has(product.id) ? "2026-08-19" : "2026-08-14");
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

test("승인된 신규 Mac 제품 5개의 사양, 가격과 구매 URL이 정확하다", () => {
    Object.entries(NEW_PRODUCT_EXPECTATIONS).forEach(([id, expectation]) => {
        const product = approvedConfig.products.find((candidate) => candidate.id === id);
        assert.ok(product, `${id} 제품이 있어야 합니다.`);
        Object.entries(expectation).forEach(([field, value]) => assert.equal(product[field], value, `${id}.${field}`));
        assert.equal(product.checkedAt, "2026-08-19");
        assert.equal(product.active, true);
    });

    assert.equal(approvedConfig.products.some((product) => product.cpuTier === "MAX"), false);
    assert.equal(approvedConfig.products.some((product) => product.id === "mac-pro16-m5max-48-2t"), false);
});

test("실제 승인 제품은 상세 화면 표시 자료와 로컬 WebP 이미지를 가진다", () => {
    approvedConfig.products.forEach((product) => {
        assert.equal(product.imagePath, `/images/products/${product.id}.webp`);
        assert.equal(product.imageAlt, `${product.brand} ${product.modelName} (${product.modelCode}) 제품 이미지`);
        assert.ok(product.shortDescription.length > 0);

        const imageFile = path.join(
            __dirname,
            "../../main/resources/static",
            product.imagePath.replace(/^\//, "")
        );
        assert.equal(fs.existsSync(imageFile), true, `${product.id} 이미지가 있어야 합니다.`);
        const header = fs.readFileSync(imageFile).subarray(0, 12);
        assert.equal(header.subarray(0, 4).toString("ascii"), "RIFF");
        assert.equal(header.subarray(8, 12).toString("ascii"), "WEBP");
        assert.deepEqual(readWebpDimensions(fs.readFileSync(imageFile)), {width: 492, height: 492});
    });

    const deletedImage = path.join(
        __dirname,
        "../../main/resources/static/images/products/mac-pro16-m5max-48-2t.webp"
    );
    assert.equal(fs.existsSync(deletedImage), false);
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

    assert.equal(combined.length, 17);
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

test("승인 제품 세트는 Mac 12개와 Windows 브랜드별 2개를 요구한다", () => {
    const products = validApprovedProducts();

    const validation = productPolicy.validateCatalog({products, approved: true, cpuTiers: CPU_TIERS});
    assert.deepEqual(validation, {valid: true, errors: []});
});

test("승인 제품 세트에는 Mac Max 제품을 포함할 수 없다", () => {
    const products = validApprovedProducts();
    products[11].cpuTier = "MAX";

    const validation = productPolicy.validateCatalog({products, approved: true, cpuTiers: CPU_TIERS});
    assert.equal(validation.valid, false);
    assert.match(validation.errors.join("\n"), /Mac Max 제품/);
});

test("쿠팡 일반가와 정확한 옵션 URL만 허용한다", () => {
    const product = sampleProduct("MACOS", "Apple", "BASIC", 1);
    product.sourceName = "Apple";
    product.priceType = "COUPON";
    product.purchaseUrl = "https://example.com/item?itemId=1";

    const validation = productPolicy.validateCatalog({
        products: Array.from({length: 20}, (_, index) => ({...product, id: `id-${index}`})),
        approved: true,
        cpuTiers: CPU_TIERS
    });
    assert.equal(validation.valid, false);
    assert.match(validation.errors.join("\n"), /GENERAL/);
    assert.match(validation.errors.join("\n"), /sourceName은 쿠팡/);
    assert.match(validation.errors.join("\n"), /HTTPS 쿠팡 URL/);
});

test("Mac Pro 기본·고메모리와 Windows HX 제품이 없으면 승인할 수 없다", () => {
    const products = [];
    for (let index = 0; index < 12; index += 1) {
        products.push(sampleProduct("MACOS", "Apple", "BASIC", index));
    }
    ["Samsung", "LG", "Lenovo", "ASUS"].forEach((brand, brandIndex) => {
        products.push(sampleProduct("WINDOWS", brand, "P_HS", 12 + brandIndex * 2));
        products.push(sampleProduct("WINDOWS", brand, "H", 13 + brandIndex * 2));
    });

    const validation = productPolicy.validateCatalog({products, approved: true, cpuTiers: CPU_TIERS});
    assert.equal(validation.valid, false);
    assert.match(validation.errors.join("\n"), /Mac Pro 기본/);
    assert.match(validation.errors.join("\n"), /Mac Pro 고메모리/);
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

function validApprovedProducts() {
    const products = [];
    for (let index = 0; index < 12; index += 1) {
        const cpuTier = index >= 8 ? "PRO" : "BASIC";
        const product = sampleProduct("MACOS", "Apple", cpuTier, index);
        product.memoryGb = index === 8 ? 24 : index > 8 ? 48 : 32;
        products.push(product);
    }
    ["Samsung", "LG", "Lenovo", "ASUS"].forEach((brand, brandIndex) => {
        products.push(sampleProduct("WINDOWS", brand, "P_HS", 12 + brandIndex * 2));
        products.push(sampleProduct("WINDOWS", brand, "HX", 13 + brandIndex * 2));
    });
    return products;
}

function readWebpDimensions(buffer) {
    const chunkType = buffer.subarray(12, 16).toString("ascii");
    if (chunkType === "VP8 ") {
        return {
            width: buffer.readUInt16LE(26) & 0x3fff,
            height: buffer.readUInt16LE(28) & 0x3fff
        };
    }
    if (chunkType === "VP8L") {
        const bits = buffer.readUInt32LE(21);
        return {
            width: (bits & 0x3fff) + 1,
            height: ((bits >> 14) & 0x3fff) + 1
        };
    }
    if (chunkType === "VP8X") {
        return {
            width: buffer.readUIntLE(24, 3) + 1,
            height: buffer.readUIntLE(27, 3) + 1
        };
    }
    assert.fail(`지원하지 않는 WebP 청크입니다: ${chunkType}`);
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
