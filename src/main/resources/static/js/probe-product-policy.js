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

    const PRODUCT_COUNT = 16;
    const OS_COUNTS = Object.freeze({MACOS: 8, WINDOWS: 8});
    const MAC_BRAND_COUNTS = Object.freeze({Apple: 8});
    const WINDOWS_BRAND_COUNTS = Object.freeze({Samsung: 2, LG: 2, Lenovo: 2, ASUS: 2});
    const MINIMUM_COVERAGE = Object.freeze([
        Object.freeze({label: "Mac 기본", minimum: 2, spec: Object.freeze({os: "MACOS", cpuTier: "BASIC", memoryGb: 24, storageGb: 512})}),
        Object.freeze({label: "Mac Pro 상향", minimum: 1, spec: Object.freeze({os: "MACOS", cpuTier: "PRO", memoryGb: 40, storageGb: 512})}),
        Object.freeze({label: "Mac Max 상향", minimum: 1, spec: Object.freeze({os: "MACOS", cpuTier: "MAX", memoryGb: 40, storageGb: 512})}),
        Object.freeze({label: "Windows 기본", minimum: 2, spec: Object.freeze({os: "WINDOWS", cpuTier: "P_HS", memoryGb: 32, storageGb: 512})}),
        Object.freeze({label: "Windows H 상향", minimum: 1, spec: Object.freeze({os: "WINDOWS", cpuTier: "H", memoryGb: 32, storageGb: 512})}),
        Object.freeze({label: "Windows HX 상향", minimum: 1, spec: Object.freeze({os: "WINDOWS", cpuTier: "HX", memoryGb: 32, storageGb: 512})})
    ]);
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
        validateCatalog,
        findMatches,
        sortByPrice,
        matchesSpec
    });
});
