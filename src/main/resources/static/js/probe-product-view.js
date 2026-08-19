(() => {
    "use strict";

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
        imageFrame.appendChild(createProductImage(product, "product-card__image"));

        const body = document.createElement("div");
        body.className = "product-card__body";

        const maker = document.createElement("span");
        maker.className = "product-card__maker";
        maker.textContent = product.brand;

        const headingRow = document.createElement("div");
        headingRow.className = "product-card__heading-row";
        const heading = document.createElement("h3");
        heading.textContent = product.modelName;
        const price = document.createElement("strong");
        price.className = "product-card__price";
        price.textContent = options.formatPrice(product.priceKrw);
        headingRow.append(heading, price);

        const specs = createSpecificationList(product, options.formatStorage, "product-card__specs");

        const source = document.createElement("p");
        source.className = "product-card__source";
        source.textContent = `${product.sourceName} · ${product.checkedAt} 확인`;

        const detailButton = document.createElement("button");
        detailButton.className = "button button--primary button--wide product-card__detail";
        detailButton.type = "button";
        detailButton.textContent = "상세 보기";
        detailButton.addEventListener("click", () => options.onDetail(product.id));

        body.append(maker, headingRow, specs, source, detailButton);
        card.append(imageFrame, body);
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
        imageFrame.appendChild(createProductImage(product, "product-detail__image"));

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
            createSpecificationList(product, options.formatStorage, "product-detail__specs")
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

    function createProductImage(product, className) {
        const image = document.createElement("img");
        image.className = className;
        image.src = product.imagePath;
        image.alt = product.imageAlt;
        image.width = 492;
        image.height = 492;
        image.decoding = "async";
        return image;
    }

    function createSpecificationList(product, formatStorage, className) {
        const list = document.createElement("dl");
        list.className = className;
        [
            ["PROCESSOR", product.cpuModelName],
            ["MEMORY", `${product.memoryGb}GB`],
            ["STORAGE", formatStorage(product.storageGb)],
            ["OS", product.os === "MACOS" ? "macOS" : "Windows"]
        ].forEach(([label, value]) => {
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
