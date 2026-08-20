((root, factory) => {
    const productPolicy = factory();
    if (typeof module === "object" && module.exports) {
        module.exports = productPolicy;
    }
    if (root) {
        root.DevicaProductPolicy = productPolicy;
    }
})(typeof window !== "undefined" ? window : globalThis, () => {
    "use strict";

    const PRODUCT_COUNT = 20;
    const OS_COUNTS = Object.freeze({MACOS: 12, WINDOWS: 8});
    const MAC_BRAND_COUNTS = Object.freeze({Apple: 12});
    const WINDOWS_BRAND_COUNTS = Object.freeze({Samsung: 2, LG: 2, Lenovo: 2, ASUS: 2});
    const MINIMUM_COVERAGE = Object.freeze([
        Object.freeze({label: "Mac 기본", minimum: 2, spec: Object.freeze({os: "MACOS", cpuTier: "BASIC", memoryGb: 24, storageGb: 512})}),
        Object.freeze({label: "Mac Pro 기본", minimum: 1, spec: Object.freeze({os: "MACOS", cpuTier: "PRO", memoryGb: 24, storageGb: 1024})}),
        Object.freeze({label: "Mac Pro 고메모리", minimum: 1, spec: Object.freeze({os: "MACOS", cpuTier: "PRO", memoryGb: 48, storageGb: 1024})}),
        Object.freeze({label: "Windows 기본", minimum: 2, spec: Object.freeze({os: "WINDOWS", cpuTier: "P_HS", memoryGb: 32, storageGb: 512})}),
        Object.freeze({label: "Windows H 상향", minimum: 1, spec: Object.freeze({os: "WINDOWS", cpuTier: "H", memoryGb: 32, storageGb: 512})}),
        Object.freeze({label: "Windows HX 상향", minimum: 1, spec: Object.freeze({os: "WINDOWS", cpuTier: "HX", memoryGb: 32, storageGb: 512})})
    ]);
    const SORT_KEYS = Object.freeze({
        RECOMMENDED: "RECOMMENDED",
        PRICE_ASC: "PRICE_ASC",
        PRICE_DESC: "PRICE_DESC"
    });
    // 비교할 권장 사양이 없는 제품. 뺄셈이 NaN이 되지 않게 무한대 대신 유한한 큰 값을 쓴다.
    const UNSCORED_GAP = Number.MAX_SAFE_INTEGER;
    const SORT_COMPARATORS = Object.freeze({
        [SORT_KEYS.PRICE_ASC]: (left, right) => left.priceKrw - right.priceKrw,
        [SORT_KEYS.PRICE_DESC]: (left, right) => right.priceKrw - left.priceKrw,
        // 권장 사양을 적게 넘긴 쪽이 위다. 같은 만큼 넘겼으면 싼 쪽을 올린다.
        [SORT_KEYS.RECOMMENDED]: (left, right, context) =>
            recommendationGap(left, context) - recommendationGap(right, context)
            || left.priceKrw - right.priceKrw
    });
    // 가격 필터는 "이 금액 이하"다. 제품 가격 분포와 무관하게 눈에 익은 눈금을 쓰고, 후보가 없는 눈금은 감춘다.
    const PRICE_STEPS = Object.freeze([1500000, 2000000, 2500000, 3000000, 4000000, 5000000]);
    const REQUIRED_STRING_FIELDS = Object.freeze([
        "id",
        "brand",
        "modelName",
        "modelCode",
        "os",
        "cpuManufacturer",
        "cpuModelName",
        "cpuTier",
        "priceType",
        "sourceName",
        "purchaseUrl",
        "checkedAt"
    ]);

    function validateCatalog({products, approved, cpuTiers}) {
        const errors = [];
        if (!Array.isArray(products)) {
            return {valid: false, errors: ["products는 배열이어야 합니다."]};
        }
        if (!approved) {
            if (products.length !== 0) {
                errors.push("미승인 제품 세트는 빈 배열이어야 합니다.");
            }
            return {valid: errors.length === 0, errors};
        }

        if (products.length !== PRODUCT_COUNT) {
            errors.push(`승인 제품은 정확히 ${PRODUCT_COUNT}개여야 합니다.`);
        }

        const ids = new Set();
        products.forEach((product, index) => validateProduct(product, index, cpuTiers, ids, errors));
        validateCount("OS", countBy(products, "os"), OS_COUNTS, errors);
        validateCount(
            "Mac 브랜드",
            countBy(products.filter((product) => product.os === "MACOS"), "brand"),
            MAC_BRAND_COUNTS,
            errors
        );
        validateCount(
            "Windows 브랜드",
            countBy(products.filter((product) => product.os === "WINDOWS"), "brand"),
            WINDOWS_BRAND_COUNTS,
            errors
        );
        validateCoverage(products, cpuTiers, errors);
        return {valid: errors.length === 0, errors};
    }

    function validateProduct(product, index, cpuTiers, ids, errors) {
        const label = `products[${index}]`;
        if (!product || typeof product !== "object") {
            errors.push(`${label}는 제품 객체여야 합니다.`);
            return;
        }
        REQUIRED_STRING_FIELDS.forEach((field) => {
            if (typeof product[field] !== "string" || product[field].trim() === "") {
                errors.push(`${label}.${field}는 비어 있지 않은 문자열이어야 합니다.`);
            }
        });
        if (ids.has(product.id)) {
            errors.push(`${label}.id가 중복되었습니다: ${product.id}`);
        }
        ids.add(product.id);
        if (!Number.isInteger(product.memoryGb) || product.memoryGb <= 0) {
            errors.push(`${label}.memoryGb는 양의 정수여야 합니다.`);
        }
        if (!Number.isInteger(product.storageGb) || product.storageGb <= 0) {
            errors.push(`${label}.storageGb는 양의 정수여야 합니다.`);
        }
        if (!Number.isInteger(product.priceKrw) || product.priceKrw <= 0) {
            errors.push(`${label}.priceKrw는 양의 정수여야 합니다.`);
        }
        if (product.priceType !== "GENERAL") {
            errors.push(`${label}.priceType은 GENERAL이어야 합니다.`);
        }
        if (product.sourceName !== "쿠팡") {
            errors.push(`${label}.sourceName은 쿠팡이어야 합니다.`);
        }
        if (product.active !== true) {
            errors.push(`${label}.active는 true여야 합니다.`);
        }
        if (!/^\d{4}-\d{2}-\d{2}$/.test(product.checkedAt || "")) {
            errors.push(`${label}.checkedAt은 YYYY-MM-DD 형식이어야 합니다.`);
        }
        if (!cpuTiers || !Array.isArray(cpuTiers[product.os]) || !cpuTiers[product.os].includes(product.cpuTier)) {
            errors.push(`${label}.cpuTier가 ${product.os} 등급표에 없습니다.`);
        }
        if (product.os === "MACOS" && product.cpuTier === "MAX") {
            errors.push(`${label}.Mac Max 제품은 승인 제품 세트에 포함할 수 없습니다.`);
        }
        validatePurchaseUrl(product.purchaseUrl, label, errors);
    }

    function validatePurchaseUrl(value, label, errors) {
        let url;
        try {
            url = new URL(value);
        } catch (error) {
            errors.push(`${label}.purchaseUrl은 유효한 URL이어야 합니다.`);
            return;
        }
        if (url.protocol !== "https:" || !["coupang.com", "www.coupang.com"].includes(url.hostname)) {
            errors.push(`${label}.purchaseUrl은 HTTPS 쿠팡 URL이어야 합니다.`);
        }
        if (!/^\/vp\/products\/\d+$/.test(url.pathname)) {
            errors.push(`${label}.purchaseUrl은 정확한 쿠팡 상품 경로여야 합니다.`);
        }
        if (!url.searchParams.get("itemId") || !url.searchParams.get("vendorItemId")) {
            errors.push(`${label}.purchaseUrl에는 itemId와 vendorItemId가 있어야 합니다.`);
        }
        const disallowed = [...url.searchParams.keys()].filter((key) => !["itemId", "vendorItemId"].includes(key));
        if (disallowed.length > 0) {
            errors.push(`${label}.purchaseUrl에는 추적 파라미터를 넣을 수 없습니다: ${disallowed.join(", ")}`);
        }
    }

    function validateCount(label, actual, expected, errors) {
        Object.entries(expected).forEach(([key, count]) => {
            if ((actual[key] || 0) !== count) {
                errors.push(`${label} ${key} 제품은 ${count}개여야 합니다.`);
            }
        });
        Object.keys(actual).filter((key) => !(key in expected)).forEach((key) => {
            errors.push(`${label}에 허용되지 않은 값이 있습니다: ${key}`);
        });
    }

    function countBy(products, field) {
        return products.reduce((counts, product) => {
            const key = product && product[field];
            if (key) {
                counts[key] = (counts[key] || 0) + 1;
            }
            return counts;
        }, {});
    }

    function validateCoverage(products, cpuTiers, errors) {
        MINIMUM_COVERAGE.forEach(({label, minimum, spec}) => {
            const count = findMatches(products, spec, cpuTiers).length;
            if (count < minimum) {
                errors.push(`${label} 사양에 맞는 제품은 최소 ${minimum}개여야 합니다.`);
            }
        });
    }

    function findMatches(products, spec, cpuTiers) {
        return sortByPrice(products.filter((product) => matchesSpec(product, spec, cpuTiers)));
    }

    function sortByPrice(products) {
        return [...products].sort((left, right) => left.priceKrw - right.priceKrw);
    }

    function sortProducts(products, sortKey, context = {}) {
        const compare = SORT_COMPARATORS[resolveSortKey(sortKey, context)];
        // 마지막은 id로 가른다. 값이 같은 제품끼리 정렬을 바꿀 때마다 자리가 뒤바뀌지 않게 한다.
        return [...products].sort((left, right) => compare(left, right, context) || compareId(left, right));
    }

    // 권장 사양 없이 추천순을 고를 길은 화면에 없지만, 들어와도 가격 낮은 순으로 떨어지게 둔다.
    function resolveSortKey(sortKey, context) {
        if (!SORT_COMPARATORS[sortKey]) {
            return SORT_KEYS.PRICE_ASC;
        }
        const hasSpecs = context.specs && Object.keys(context.specs).length > 0 && context.cpuTiers;
        return sortKey === SORT_KEYS.RECOMMENDED && !hasSpecs ? SORT_KEYS.PRICE_ASC : sortKey;
    }

    // 권장 사양과 각 축에서 얼마나 떨어졌는지 더한다. 0이면 사양이 정확히 맞는 제품이다.
    // 모자란 쪽도 거리다. 부호를 남기면 사양 미달 제품이 딱 맞는 제품보다 위로 올라간다.
    function recommendationGap(product, {specs, cpuTiers}) {
        const spec = specs && specs[product.os];
        if (!spec) {
            return UNSCORED_GAP;
        }
        const tiers = cpuTiers[spec.os];
        return Math.abs(tiers.indexOf(product.cpuTier) - tiers.indexOf(spec.cpuTier))
            + doublingGap(product.memoryGb, spec.memoryGb)
            + doublingGap(product.storageGb, spec.storageGb);
    }

    // 용량은 배씩 오른다. 배수로 재야 "CPU 한 등급 차이 ≈ 용량 두 배"로 같은 눈금에 놓인다.
    function doublingGap(value, baseline) {
        return Math.abs(Math.log2(value / baseline));
    }

    function compareId(left, right) {
        if (left.id === right.id) {
            return 0;
        }
        return left.id < right.id ? -1 : 1;
    }

    // 선택지를 목록에 실제로 있는 값으로만 만든다. 그래야 조건 하나만 걸어서 0건이 되는 일이 없다.
    function buildFilterOptions(products, cpuTiers) {
        const active = products.filter((product) => product.active);
        return {
            maxPriceKrw: PRICE_STEPS.filter((step) => active.some((product) => product.priceKrw <= step)),
            // 제품이 없는 등급은 바로 아래 등급과 결과가 같아 고를 이유가 없다. 실제로 가진 등급만 남긴다.
            cpu: Object.keys(cpuTiers)
                .map((os) => ({
                    os,
                    tiers: cpuTiers[os].filter((tier) => active.some(
                        (product) => product.os === os && product.cpuTier === tier
                    ))
                }))
                .filter((group) => group.tiers.length > 0),
            minMemoryGb: uniqueAscending(active, "memoryGb"),
            minStorageGb: uniqueAscending(active, "storageGb")
        };
    }

    function applySearchCondition(products, condition, cpuTiers) {
        const {maxPriceKrw, cpu, minMemoryGb, minStorageGb} = condition || {};
        return products.filter((product) => {
            if (maxPriceKrw != null && product.priceKrw > maxPriceKrw) {
                return false;
            }
            if (cpu && !(product.os === cpu.os && atLeastTier(product, cpu.os, cpu.tier, cpuTiers))) {
                return false;
            }
            if (minMemoryGb != null && product.memoryGb < minMemoryGb) {
                return false;
            }
            return !(minStorageGb != null && product.storageGb < minStorageGb);
        });
    }

    function atLeastTier(product, os, tier, cpuTiers) {
        return cpuTiers[os].indexOf(product.cpuTier) >= cpuTiers[os].indexOf(tier);
    }

    function uniqueAscending(products, field) {
        return [...new Set(products.map((product) => product[field]))].sort((left, right) => left - right);
    }

    function matchesSpec(product, spec, cpuTiers) {
        return product.active
            && product.os === spec.os
            && cpuTiers[spec.os].indexOf(product.cpuTier) >= cpuTiers[spec.os].indexOf(spec.cpuTier)
            && product.memoryGb >= spec.memoryGb
            && product.storageGb >= spec.storageGb;
    }

    return Object.freeze({
        PRODUCT_COUNT,
        OS_COUNTS,
        MAC_BRAND_COUNTS,
        WINDOWS_BRAND_COUNTS,
        MINIMUM_COVERAGE,
        SORT_KEYS,
        PRICE_STEPS,
        validateCatalog,
        findMatches,
        sortByPrice,
        sortProducts,
        buildFilterOptions,
        applySearchCondition,
        matchesSpec
    });
});
