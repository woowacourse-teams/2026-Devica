(() => {
    "use strict";

    const config = window.DEVICA_PROBE_CONFIG;
    const policy = window.DevicaProbePolicy;
    const productPolicy = window.DevicaProductPolicy;
    const productView = window.DevicaProductView;
    if (!config || !config.approved || !policy || !productPolicy || !productView) {
        return;
    }

    const VIEW = Object.freeze({
        INTRO: "INTRO",
        QUESTION: "QUESTION",
        RESULT: "RESULT",
        PRODUCT_LIST: "PRODUCT_LIST",
        PRODUCT_DETAIL: "PRODUCT_DETAIL"
    });

    // 전체 제품 목록에서 "뒤로"를 눌렀을 때 돌아갈 수 있는 화면. 제품 화면끼리 서로 되돌아가지 않게 한다.
    const RETURNABLE_VIEWS = Object.freeze([VIEW.INTRO, VIEW.QUESTION, VIEW.RESULT]);

    const elements = {
        navProductList: document.querySelector("#nav-product-list-button"),
        intro: document.querySelector("#intro-view"),
        initialSpecs: document.querySelector("#initial-spec-list"),
        start: document.querySelector("#start-button"),
        question: document.querySelector("#question-view"),
        questionContent: document.querySelector("#question-content"),
        progressCount: document.querySelector("#progress-count"),
        progressValue: document.querySelector("#progress-value"),
        previous: document.querySelector("#previous-button"),
        next: document.querySelector("#next-button"),
        result: document.querySelector("#result-view"),
        resultOs: document.querySelector("#result-os-control"),
        specList: document.querySelector("#spec-list"),
        editSpec: document.querySelector("#edit-spec-button"),
        productButton: document.querySelector("#product-button"),
        productListView: document.querySelector("#product-list-view"),
        productListBack: document.querySelector("#product-list-back-button"),
        productListEyebrow: document.querySelector("#product-list-eyebrow"),
        productListTitle: document.querySelector("#product-list-title"),
        productListDescription: document.querySelector("#product-list-description"),
        productList: document.querySelector("#product-list"),
        productDetailView: document.querySelector("#product-detail-view"),
        productDetail: document.querySelector("#product-detail"),
        feedbackComplete: document.querySelector("#feedback-complete")
    };

    const state = {
        sessionId: getSessionId(),
        questionIndex: 0,
        visibleQuestions: [],
        answers: new Map(),
        currentSpec: {},
        currentSpecSubmitted: false,
        calculation: null,
        finalSpecs: new Map(),
        resultViewMode: "BOTH",
        editMode: false,
        activeView: VIEW.INTRO,
        matchedProducts: [],
        productListMode: "MATCHED",
        productListReturnView: VIEW.RESULT,
        selectedProductId: null,
        lastViewedQuestionId: null,
        submittedFeedback: new Set()
    };

    renderInitialBaselines();
    elements.navProductList.addEventListener("click", showAllProducts);
    elements.start.addEventListener("click", startRecommendation);
    elements.previous.addEventListener("click", showPreviousQuestion);
    elements.next.addEventListener("click", showNextQuestion);
    elements.editSpec.addEventListener("click", toggleSpecificationEdit);
    elements.productButton.addEventListener("click", showProducts);
    elements.productListBack.addEventListener("click", leaveProductList);
    document.querySelectorAll("[data-feedback]").forEach(bindFeedbackGroup);

    function renderInitialBaselines() {
        elements.initialSpecs.replaceChildren(
            createBaselineCard(policy.OS.MACOS, config.baselineTracks[policy.OS.MACOS]),
            createBaselineCard(policy.OS.WINDOWS, config.baselineTracks[policy.OS.WINDOWS])
        );
    }

    function createBaselineCard(os, spec) {
        const card = document.createElement("article");
        card.className = "baseline-card";
        card.innerHTML = `<p class="eyebrow"></p><h2></h2><dl></dl>`;
        card.querySelector(".eyebrow").textContent = os;
        card.querySelector("h2").textContent = osLabel(os);
        const list = card.querySelector("dl");
        [
            ["PROCESSOR", policy.cpuLabel(os, spec.cpuTier)],
            ["MEMORY", `${spec.memoryGb}GB`],
            ["STORAGE", formatStorage(spec.storageGb)]
        ].forEach(([label, value]) => {
            const term = document.createElement("dt");
            term.textContent = label;
            const description = document.createElement("dd");
            description.textContent = value;
            list.append(term, description);
        });
        return card;
    }

    function startRecommendation() {
        state.visibleQuestions = resolveVisibleQuestions();
        state.questionIndex = 0;
        activateView(VIEW.QUESTION);
        recordEvent("RECOMMENDATION_STARTED");
        renderQuestion();
    }

    function resolveVisibleQuestions() {
        const answers = answerObject();
        return config.questions.filter((question) => !question.isVisible || question.isVisible(answers));
    }

    function renderQuestion() {
        state.visibleQuestions = resolveVisibleQuestions();
        if (state.questionIndex >= state.visibleQuestions.length) {
            state.questionIndex = state.visibleQuestions.length - 1;
        }
        const question = state.visibleQuestions[state.questionIndex];
        if (!question) {
            completeRecommendation();
            return;
        }

        elements.progressCount.textContent = `${state.questionIndex + 1} / ${state.visibleQuestions.length}`;
        elements.progressValue.style.width = `${((state.questionIndex + 1) / state.visibleQuestions.length) * 100}%`;
        elements.previous.disabled = state.questionIndex === 0;
        elements.next.disabled = !isQuestionComplete(question);
        elements.next.textContent = state.questionIndex === state.visibleQuestions.length - 1 ? "결과 보기" : "다음";
        elements.questionContent.replaceChildren(
            question.kind === "current-spec" ? createCurrentSpecContent(question) : createQuestionContent(question)
        );
        if (state.lastViewedQuestionId !== question.id) {
            state.lastViewedQuestionId = question.id;
            recordEvent("QUESTION_VIEWED", {questionId: question.id});
        }
    }

    function createCurrentSpecContent(question) {
        const fragment = createQuestionHeading(question);
        const form = document.createElement("div");
        form.className = "current-spec-form";
        form.append(
            createSelectField("CURRENT_OS", "OS", [
                [policy.OS.MACOS, "Mac"],
                [policy.OS.WINDOWS, "Windows"]
            ], state.currentSpec.os, updateCurrentOs),
            createSelectField("CURRENT_CPU", "CPU", currentCpuOptions(), state.currentSpec.cpuTier, updateCurrentCpu, !state.currentSpec.os),
            createSelectField("CURRENT_MEMORY", "RAM(GB)", [
                [8, "8GB 이하"], [16, "16GB"], [24, "24GB"], [32, "32GB"], [64, "64GB 이상"]
            ], state.currentSpec.memoryGb, (value) => updateCurrentNumber("memoryGb", "CURRENT_MEMORY", value)),
            createSelectField("CURRENT_STORAGE", "SSD 용량(GB)", [
                [256, "256GB 이하"], [512, "256GB 초과 1TB 미만"], [1024, "1TB 이상"]
            ], state.currentSpec.storageGb, (value) => updateCurrentNumber("storageGb", "CURRENT_STORAGE", value))
        );
        fragment.appendChild(form);
        return fragment;
    }

    function createSelectField(id, label, options, value, onChange, disabled = false) {
        const wrapper = document.createElement("label");
        wrapper.className = "current-spec-field";
        wrapper.htmlFor = id;
        const title = document.createElement("span");
        title.textContent = label;
        const select = document.createElement("select");
        select.id = id;
        select.disabled = disabled;
        select.appendChild(new Option(disabled ? "OS를 먼저 선택해 주세요" : "선택하지 않음", ""));
        options.forEach(([optionValue, optionLabel]) => select.appendChild(new Option(optionLabel, String(optionValue))));
        select.value = value == null ? "" : String(value);
        select.addEventListener("change", () => onChange(select.value || null));
        wrapper.append(title, select);
        return wrapper;
    }

    function currentCpuOptions() {
        if (!state.currentSpec.os) {
            return [];
        }
        return policy.CPU_TIERS[state.currentSpec.os].map((tier) => [tier, policy.cpuLabel(state.currentSpec.os, tier)]);
    }

    function updateCurrentOs(value) {
        const previous = state.currentSpec.os;
        if (value == null) {
            delete state.currentSpec.os;
        } else {
            state.currentSpec.os = value;
        }
        if (previous !== value && state.currentSpec.cpuTier != null) {
            delete state.currentSpec.cpuTier;
            recordEvent("ANSWER_CHANGED", {questionId: "CURRENT_CPU", optionId: null});
        }
        recordEvent(previous ? "ANSWER_CHANGED" : "QUESTION_ANSWERED", {questionId: "CURRENT_OS", optionId: value});
        renderQuestion();
    }

    function updateCurrentCpu(value) {
        updateCurrentValue("cpuTier", "CURRENT_CPU", value);
    }

    function updateCurrentNumber(field, questionId, value) {
        updateCurrentValue(field, questionId, value == null ? null : Number(value));
    }

    function updateCurrentValue(field, questionId, value) {
        const previous = state.currentSpec[field];
        if (value == null) {
            delete state.currentSpec[field];
        } else {
            state.currentSpec[field] = value;
        }
        recordEvent(previous == null ? "QUESTION_ANSWERED" : "ANSWER_CHANGED", {
            questionId,
            optionId: value == null ? null : String(value)
        });
    }

    function createQuestionContent(question) {
        const fragment = createQuestionHeading(question);
        question.groups.forEach((group) => {
            const groupContainer = document.createElement("fieldset");
            groupContainer.className = "question-group";
            if (group.label) {
                const legend = document.createElement("legend");
                legend.textContent = group.label;
                groupContainer.appendChild(legend);
            }
            group.options.forEach((option) => groupContainer.appendChild(createOption(question, group, option)));
            fragment.appendChild(groupContainer);
        });
        return fragment;
    }

    function createQuestionHeading(question) {
        const fragment = document.createDocumentFragment();
        const heading = document.createElement("h2");
        heading.className = "question-heading";
        heading.textContent = question.title;
        fragment.appendChild(heading);
        if (question.description) {
            const description = document.createElement("p");
            description.className = "question-description";
            description.textContent = question.description;
            fragment.appendChild(description);
        }
        return fragment;
    }

    function createOption(question, group, option) {
        const button = document.createElement("button");
        const answerKey = group.id || question.id;
        button.type = "button";
        button.className = "question-option";
        button.setAttribute("aria-pressed", String(isSelected(answerKey, option.id)));

        const title = document.createElement("span");
        title.className = "question-option__title";
        title.textContent = option.label;
        button.appendChild(title);
        if (option.description) {
            const description = document.createElement("span");
            description.className = "question-option__description";
            description.textContent = option.description;
            button.appendChild(description);
        }
        button.addEventListener("click", () => selectOption(question, group, option.id));
        return button;
    }

    function selectOption(question, group, optionId) {
        const answerKey = group.id || question.id;
        const previous = state.answers.get(answerKey);
        if (group.multiple) {
            const selected = Array.isArray(previous) ? [...previous] : [];
            const next = toggleMultiple(selected, optionId, group.exclusiveOptionId);
            if (next.length === 0) {
                state.answers.delete(answerKey);
            } else {
                state.answers.set(answerKey, next);
            }
        } else {
            state.answers.set(answerKey, optionId);
        }
        if (!Array.isArray(state.answers.get("Q2")) || !state.answers.get("Q2").includes("JAVA_FAMILY")) {
            state.answers.delete("Q7");
        }
        const current = state.answers.get(answerKey);
        recalculate();
        recordEvent(previous == null ? "QUESTION_ANSWERED" : "ANSWER_CHANGED", {
            questionId: answerKey,
            optionId: Array.isArray(current) ? current.join(",") : current
        });
        recordEvent("SPEC_ADJUSTED", {questionId: answerKey, optionId});
        renderQuestion();
    }

    function toggleMultiple(selected, optionId, exclusiveOptionId) {
        if (optionId === exclusiveOptionId) {
            return selected.length === 1 && selected[0] === exclusiveOptionId ? [] : [exclusiveOptionId];
        }
        const withoutExclusive = selected.filter((id) => id !== exclusiveOptionId);
        return withoutExclusive.includes(optionId)
            ? withoutExclusive.filter((id) => id !== optionId)
            : [...withoutExclusive, optionId];
    }

    function isSelected(answerKey, optionId) {
        const answer = state.answers.get(answerKey);
        return Array.isArray(answer) ? answer.includes(optionId) : answer === optionId;
    }

    function isQuestionComplete(question) {
        if (question.kind === "current-spec") {
            return true;
        }
        return question.groups.every((group) => {
            const answer = state.answers.get(group.id || question.id);
            return Array.isArray(answer) ? answer.length > 0 : answer != null;
        });
    }

    function showPreviousQuestion() {
        if (state.questionIndex === 0) {
            return;
        }
        state.questionIndex -= 1;
        state.lastViewedQuestionId = null;
        renderQuestion();
    }

    function showNextQuestion() {
        const question = state.visibleQuestions[state.questionIndex];
        if (!isQuestionComplete(question)) {
            return;
        }
        if (question.kind === "current-spec" && !state.currentSpecSubmitted) {
            state.currentSpecSubmitted = true;
            const status = policy.currentSpecSubmissionStatus(state.currentSpec);
            recordEvent("CURRENT_SPEC_SUBMITTED", {optionId: status});
        }
        state.visibleQuestions = resolveVisibleQuestions();
        if (state.questionIndex >= state.visibleQuestions.length - 1) {
            completeRecommendation();
            return;
        }
        state.questionIndex += 1;
        state.lastViewedQuestionId = null;
        renderQuestion();
    }

    function recalculate() {
        state.calculation = config.calculateRecommendation(answerObject(), state.currentSpec);
    }

    function completeRecommendation() {
        recalculate();
        state.finalSpecs.clear();
        Object.values(policy.OS).forEach((os) => {
            state.finalSpecs.set(os, policy.cloneSpec(state.calculation.tracks[os].calculatedSpec));
        });
        state.resultViewMode = state.calculation.activeOs.length === 2 ? "BOTH" : state.calculation.activeOs[0];
        state.editMode = false;
        resetProductResults();
        activateView(VIEW.RESULT);
        renderSpecification();
        recordEvent("RECOMMENDATION_COMPLETED");
    }

    function renderSpecification() {
        renderResultOsControl();
        const osList = visibleResultOs();
        elements.specList.replaceChildren(...osList.map(createSpecCard));
        elements.editSpec.textContent = state.editMode ? "수정 완료" : "사양 직접 수정";
        const productSetReady = isProductSetReady();
        elements.productButton.disabled = !productSetReady;
        elements.productButton.textContent = productSetReady
            ? "이 사양으로 제품 보기"
            : "제품 목록 승인 대기";
    }

    function renderResultOsControl() {
        const select = document.createElement("select");
        select.id = "result-os-select";
        [[policy.OS.MACOS, "Mac"], [policy.OS.WINDOWS, "Windows"], ["BOTH", "둘 다"]]
            .forEach(([value, label]) => select.appendChild(new Option(label, value)));
        select.value = state.resultViewMode;
        select.addEventListener("change", () => {
            state.resultViewMode = select.value;
            resetProductResults();
            renderSpecification();
            recordEvent("FINAL_SPEC_EDITED", {questionId: "RESULT_OS", optionId: select.value});
        });
        elements.resultOs.replaceChildren(select);
    }

    function createSpecCard(os) {
        const track = state.calculation.tracks[os];
        const finalSpec = state.finalSpecs.get(os);
        const card = document.createElement("article");
        card.className = "spec-card";
        const heading = document.createElement("h3");
        heading.textContent = `${osLabel(os)} 권장 사양`;
        card.appendChild(heading);
        card.append(
            createSpecRow(os, "OS", osLabel(os), track.reasons.os.join(" ")),
            createEditableSpecRow(os, "PROCESSOR", "cpuTier", finalSpec.cpuTier, track),
            createEditableSpecRow(os, "MEMORY", "memoryGb", finalSpec.memoryGb, track),
            createEditableSpecRow(os, "STORAGE", "storageGb", finalSpec.storageGb, track)
        );
        return card;
    }

    function createEditableSpecRow(os, label, field, value, track) {
        if (!state.editMode) {
            return createSpecRow(os, label, formatSpecValue(os, field, value), track.reasons[reasonKey(field)].join(" "));
        }
        const row = createSpecRow(os, label, "", track.reasons[reasonKey(field)].join(" "));
        const valueContainer = row.querySelector(".spec-row__value");
        const select = document.createElement("select");
        specOptions(os, field).forEach(([optionValue, optionLabel]) => {
            select.appendChild(new Option(optionLabel, String(optionValue)));
        });
        select.value = String(value);
        select.addEventListener("change", () => updateFinalSpec(os, field, select));
        valueContainer.appendChild(select);
        return row;
    }

    function createSpecRow(os, label, value, reason) {
        const row = document.createElement("section");
        row.className = "spec-row";
        row.dataset.os = os;
        row.innerHTML = `<span class="spec-row__label"></span><strong class="spec-row__value"></strong><p class="spec-row__reason"></p>`;
        row.querySelector(".spec-row__label").textContent = label;
        row.querySelector(".spec-row__value").textContent = value;
        row.querySelector(".spec-row__reason").textContent = reason;
        return row;
    }

    function specOptions(os, field) {
        if (field === "cpuTier") {
            return policy.CPU_TIERS[os].map((tier) => [tier, policy.cpuLabel(os, tier)]);
        }
        if (field === "memoryGb") {
            return policy.MEMORY_OPTIONS.map((value) => [value, `${value}GB`]);
        }
        return policy.STORAGE_OPTIONS.map((value) => [value, formatStorage(value)]);
    }

    function updateFinalSpec(os, field, select) {
        const finalSpec = state.finalSpecs.get(os);
        const calculated = state.calculation.tracks[os].calculatedSpec[field];
        const previous = finalSpec[field];
        const next = field === "cpuTier" ? select.value : Number(select.value);
        if (isLower(os, field, next, calculated) && !window.confirm(loweringMessage(os, field))) {
            select.value = String(previous);
            return;
        }
        finalSpec[field] = next;
        resetProductResults();
        recordEvent("FINAL_SPEC_EDITED", {questionId: `${os}_${field}`, optionId: String(next)});
    }

    function isLower(os, field, value, calculated) {
        if (field === "cpuTier") {
            return policy.CPU_TIERS[os].indexOf(value) < policy.CPU_TIERS[os].indexOf(calculated);
        }
        return Number(value) < Number(calculated);
    }

    function loweringMessage(os, field) {
        const expansion = os === policy.OS.MACOS
            ? "Mac은 메모리와 저장 공간을 나중에 늘릴 수 없습니다."
            : "Windows는 모델에 따라 메모리나 M.2 SSD를 추가할 수 있습니다.";
        return `권장값보다 낮은 ${fieldLabel(field)}을 선택했습니다. ${expansion} 이 값으로 변경할까요?`;
    }

    function toggleSpecificationEdit() {
        state.editMode = !state.editMode;
        renderSpecification();
    }

    function visibleResultOs() {
        return state.resultViewMode === "BOTH"
            ? [policy.OS.MACOS, policy.OS.WINDOWS]
            : [state.resultViewMode];
    }

    function showProducts() {
        if (!isProductSetReady()) {
            return;
        }
        state.productListMode = "MATCHED";
        state.matchedProducts = productPolicy.sortByPrice(visibleResultOs().flatMap((os) => {
            const spec = state.finalSpecs.get(os);
            return productPolicy.findMatches(config.products, spec, policy.CPU_TIERS);
        }));
        openProductList();
    }

    function showAllProducts() {
        if (!isProductSetReady()) {
            return;
        }
        state.productListMode = "ALL";
        state.productListReturnView = RETURNABLE_VIEWS.includes(state.activeView) ? state.activeView : VIEW.RESULT;
        state.matchedProducts = productPolicy.sortByPrice(config.products.filter((product) => product.active));
        openProductList();
    }

    function openProductList() {
        state.selectedProductId = null;
        renderProductListHeading();
        renderProductList();
        activateView(VIEW.PRODUCT_LIST);
        recordEvent("PRODUCT_LIST_VIEWED", {optionId: config.productSetVersion});
        scrollToViewStart();
    }

    function renderProductListHeading() {
        const showsAll = state.productListMode === "ALL";
        elements.productListEyebrow.textContent = showsAll ? "ALL PRODUCTS" : "MATCHED PRODUCTS";
        elements.productListTitle.textContent = showsAll ? "전체 제품" : "추천 노트북";
        elements.productListDescription.textContent = showsAll
            ? "지금 비교할 수 있는 노트북 전체입니다."
            : "확정한 사양을 모두 충족하는 제품만 보여드려요.";
        elements.productListBack.textContent = showsAll ? backLabel(state.productListReturnView) : "← 권장 사양으로";
    }

    function backLabel(view) {
        if (view === VIEW.INTRO) {
            return "← 처음으로";
        }
        return view === VIEW.QUESTION ? "← 질문으로" : "← 권장 사양으로";
    }

    function leaveProductList() {
        if (state.productListMode !== "ALL") {
            showRecommendationResult();
            return;
        }
        state.selectedProductId = null;
        activateView(state.productListReturnView);
        scrollToViewStart();
    }

    function isProductSetReady() {
        return config.productSetApproved && config.productSetValidation.valid;
    }

    function renderProductList() {
        if (state.matchedProducts.length === 0) {
            const empty = document.createElement("p");
            empty.className = "empty-result";
            empty.textContent = "조건에 맞는 제품이 없습니다. 사양을 직접 조정해 다시 확인해 주세요.";
            elements.productList.replaceChildren(empty);
            return;
        }
        elements.productList.replaceChildren(...state.matchedProducts.map((product) => {
            return productView.createProductCard(product, {
                formatPrice,
                formatStorage,
                onDetail: showProductDetail
            });
        }));
    }

    function showProductDetail(productId) {
        const product = productView.findProduct(state.matchedProducts, productId);
        if (!product) {
            return;
        }
        state.selectedProductId = product.id;
        // 상세 진입은 experiment_event ENUM에 없어 PostHog에만 보낸다. DB 집계가 필요해지면 ENUM을 확장한다.
        window.posthog?.capture("PRODUCT_DETAIL_VIEWED", {
            optionId: product.id,
            sessionId: state.sessionId,
            questionSetVersion: config.version
        });
        elements.productDetail.replaceChildren(productView.createProductDetail(product, {
            formatPrice,
            formatStorage,
            onBack: showProductList,
            onPurchase: (selectedId) => recordEvent("PRODUCT_CLICKED", {optionId: selectedId})
        }));
        activateView(VIEW.PRODUCT_DETAIL);
        scrollToViewStart();
    }

    function showProductList() {
        if (state.matchedProducts.length === 0 && state.selectedProductId == null) {
            showRecommendationResult();
            return;
        }
        activateView(VIEW.PRODUCT_LIST);
        scrollToViewStart();
    }

    function showRecommendationResult() {
        state.selectedProductId = null;
        activateView(VIEW.RESULT);
        scrollToViewStart();
    }

    function resetProductResults() {
        state.matchedProducts = [];
        state.selectedProductId = null;
        elements.productList.replaceChildren();
        elements.productDetail.replaceChildren();
    }

    function activateView(view) {
        state.activeView = view;
        [
            [elements.intro, VIEW.INTRO],
            [elements.question, VIEW.QUESTION],
            [elements.result, VIEW.RESULT],
            [elements.productListView, VIEW.PRODUCT_LIST],
            [elements.productDetailView, VIEW.PRODUCT_DETAIL]
        ].forEach(([element, targetView]) => {
            element.classList.toggle("is-hidden", targetView !== view);
        });
    }

    function scrollToViewStart() {
        window.scrollTo({top: 0, behavior: "smooth"});
    }

    function bindFeedbackGroup(group) {
        const eventName = group.dataset.feedback;
        group.querySelectorAll("[data-option]").forEach((button) => {
            button.addEventListener("click", () => {
                group.querySelectorAll("[data-option]").forEach((candidate) => {
                    candidate.setAttribute("aria-pressed", String(candidate === button));
                });
                state.submittedFeedback.add(eventName);
                recordEvent(eventName, {optionId: button.dataset.option});
                if (state.submittedFeedback.size === 2) {
                    elements.feedbackComplete.classList.remove("is-hidden");
                }
            });
        });
    }

    function recordEvent(eventName, details = {}) {
        // DB로 보내는 이벤트를 PostHog로도 그대로 미러링한다.
        window.posthog?.capture(eventName, {
            ...details,
            sessionId: state.sessionId,
            questionSetVersion: config.version
        });

        const request = {
            sessionId: state.sessionId,
            questionSetVersion: config.version,
            eventName,
            questionId: details.questionId || null,
            optionId: details.optionId || null,
            recommendationSnapshots: recommendationSnapshots(),
            occurredAt: new Date().toISOString()
        };

        fetch("/api/probe/events", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(request),
            keepalive: true
        }).catch(() => {
            // 이벤트 실패가 사용자 추천 흐름을 막지 않도록 한다.
        });
    }

    function recommendationSnapshots() {
        if (!state.calculation) {
            return [];
        }
        const snapshotOs = state.finalSpecs.size > 0 ? visibleResultOs() : state.calculation.activeOs;
        return snapshotOs.map((os) => {
            const spec = state.finalSpecs.get(os) || state.calculation.tracks[os].calculatedSpec;
            return {os: spec.os, memoryGb: spec.memoryGb, storageGb: spec.storageGb, cpuTier: spec.cpuTier};
        });
    }

    function answerObject() {
        return Object.fromEntries(state.answers);
    }

    function formatSpecValue(os, field, value) {
        if (field === "cpuTier") {
            return policy.cpuLabel(os, value);
        }
        return field === "storageGb" ? formatStorage(value) : `${value}GB`;
    }

    function formatStorage(value) {
        return Number(value) >= 1024 ? `${Number(value) / 1024}TB` : `${value}GB`;
    }

    function formatPrice(value) {
        return `${Number(value).toLocaleString("ko-KR")}원`;
    }

    function reasonKey(field) {
        return {cpuTier: "cpu", memoryGb: "memory", storageGb: "storage"}[field];
    }

    function fieldLabel(field) {
        return {cpuTier: "CPU", memoryGb: "메모리", storageGb: "저장 공간"}[field];
    }

    function osLabel(os) {
        return os === policy.OS.MACOS ? "Mac" : "Windows";
    }

    function getSessionId() {
        const key = "devica-probe-session-id";
        const existing = sessionStorage.getItem(key);
        if (existing) {
            return existing;
        }
        const created = crypto.randomUUID();
        sessionStorage.setItem(key, created);
        return created;
    }
})();
