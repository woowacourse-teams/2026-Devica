(() => {
    "use strict";

    const SPEC_FIELDS = {
        PROCESSOR: (product) => ["PROCESSOR", product.cpuModelName],
        MEMORY: (product) => ["MEMORY", `${product.memoryGb}GB`],
        STORAGE: (product, formatStorage) => ["STORAGE", formatStorage(product.storageGb)],
        OS: (product) => ["OS", osLabel(product.os)]
    };
    // 목록 카드는 한 화면에 담아야 해서 3행만 쓴다. OS는 브랜드 줄에 붙인다.
    const CARD_SPECS = ["PROCESSOR", "MEMORY", "STORAGE"];
    const DETAIL_SPECS = ["PROCESSOR", "MEMORY", "STORAGE", "OS"];

    const api = {
        createProductCard,
        createProductDetail,
        findProduct
    };

    if (typeof module !== "undefined" && module.exports) {
        module.exports = api;
    }
    if (typeof window !== "undefined") {
        window.DevicaProductView = Object.freeze(api);
    }

    function findProduct(products, productId) {
        if (!Array.isArray(products) || !productId) {
            return null;
        }
        return products.find((product) => product.id === productId) || null;
    }

    function createProductCard(product, options) {
        const card = document.createElement("article");
        card.className = "product-card";

        const imageFrame = document.createElement("div");
        imageFrame.className = "product-card__image-frame";
        imageFrame.appendChild(createProductImage(product, "product-card__image", options.eagerImage));

        const body = document.createElement("div");
        body.className = "product-card__body";

        const headingRow = document.createElement("div");
        headingRow.className = "product-card__heading-row";

        const identity = document.createElement("div");
        const maker = document.createElement("span");
        maker.className = "product-card__maker";
        // 카드 사양은 3행으로 줄였으므로 OS는 브랜드 줄에 붙여 목록에서도 구분되게 한다.
        maker.textContent = `${product.brand} · ${osLabel(product.os)}`;
        const heading = document.createElement("h3");
        heading.textContent = product.modelName;
        identity.append(maker, heading);

        const price = document.createElement("strong");
        price.className = "product-card__price";
        price.textContent = options.formatPrice(product.priceKrw);
        headingRow.append(identity, price);

        const specs = createSpecificationList(product, options.formatStorage, "product-card__specs", CARD_SPECS);
        body.append(headingRow, specs);

        const action = document.createElement("div");
        action.className = "product-card__action";
        const detailButton = document.createElement("button");
        detailButton.className = "button button--primary button--wide product-card__detail";
        detailButton.type = "button";
        detailButton.textContent = "상세 보기";
        detailButton.addEventListener("click", () => options.onDetail(product.id));
        action.appendChild(detailButton);

        card.append(imageFrame, body, action);
        return card;
    }

    function createProductDetail(product, options) {
        const fragment = document.createDocumentFragment();

        const backButton = document.createElement("button");
        backButton.className = "text-button product-detail__back";
        backButton.type = "button";
        backButton.textContent = "← 제품 목록으로";
        backButton.addEventListener("click", options.onBack);

        const detail = document.createElement("article");
        detail.className = "product-detail";

        const imageFrame = document.createElement("div");
        imageFrame.className = "product-detail__image-frame";
        imageFrame.appendChild(createProductImage(product, "product-detail__image", true));

        const summary = document.createElement("section");
        summary.className = "product-detail__summary";

        const maker = document.createElement("p");
        maker.className = "eyebrow";
        maker.textContent = `${product.brand} · ${product.modelCode}`;

        const heading = document.createElement("h2");
        heading.id = "product-detail-title";
        heading.textContent = product.modelName;

        const description = document.createElement("p");
        description.className = "product-detail__description";
        description.textContent = product.shortDescription;

        const price = document.createElement("strong");
        price.className = "product-detail__price";
        price.textContent = options.formatPrice(product.priceKrw);

        const source = document.createElement("p");
        source.className = "product-detail__source";
        source.textContent = `${product.sourceName} 일반 판매가 · ${product.checkedAt} 확인`;

        summary.append(maker, heading, description, price, source);

        const specification = document.createElement("section");
        specification.className = "product-detail__specification";
        const specificationHeading = document.createElement("h3");
        specificationHeading.textContent = "상세 사양";
        specification.append(
            specificationHeading,
            createSpecificationList(product, options.formatStorage, "product-detail__specs", DETAIL_SPECS)
        );

        const purchase = document.createElement("a");
        purchase.className = "button button--primary product-detail__purchase";
        purchase.href = product.purchaseUrl;
        purchase.target = "_blank";
        purchase.rel = "noopener noreferrer";
        purchase.textContent = "구매하기";
        purchase.addEventListener("click", () => options.onPurchase(product.id));

        detail.append(imageFrame, summary, specification, purchase);
        fragment.append(backButton, detail);
        return fragment;
    }

    function createProductImage(product, className, eager) {
        const image = document.createElement("img");
        image.className = className;
        image.src = product.imagePath;
        image.alt = product.imageAlt;
        // 크기는 CSS가 정한다. width/height를 박으면 카드가 이미지 비율에 끌려간다.
        image.decoding = "async";
        image.loading = eager ? "eager" : "lazy";
        return image;
    }

    function osLabel(os) {
        return os === "MACOS" ? "macOS" : "Windows";
    }

    function createSpecificationList(product, formatStorage, className, fields) {
        const list = document.createElement("dl");
        list.className = className;
        fields.map((field) => SPEC_FIELDS[field](product, formatStorage)).forEach(([label, value]) => {
            const item = document.createElement("div");
            item.className = "product-spec-item";
            const term = document.createElement("dt");
            term.textContent = label;
            const description = document.createElement("dd");
            description.textContent = value;
            item.append(term, description);
            list.appendChild(item);
        });
        return list;
    }
})();
