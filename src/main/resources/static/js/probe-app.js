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
        SEARCH_LOADING: "SEARCH_LOADING",
        PRODUCT_LIST: "PRODUCT_LIST",
        PRODUCT_DETAIL: "PRODUCT_DETAIL"
    });

    // 매칭은 즉시 끝나지만 답변이 실제로 쓰였다는 걸 보여주려고 화면만 붙잡아 둔다.
    const SEARCH_LOADING_MS = 1800;

    // 전체 제품 목록에서 "뒤로"를 눌렀을 때 돌아갈 수 있는 화면. 제품 화면끼리 서로 되돌아가지 않게 한다.
    const RETURNABLE_VIEWS = Object.freeze([VIEW.INTRO, VIEW.QUESTION, VIEW.RESULT]);

    const elements = {
        navProductList: document.querySelector("#nav-product-list-button"),
        intro: document.querySelector("#intro-view"),
        initialSpecs: document.querySelector("#initial-spec-list"),
        start: document.querySelector("#start-button"),
        baselineProduct: document.querySelector("#baseline-product-button"),
        question: document.querySelector("#question-view"),
        questionContent: document.querySelector("#question-content"),
        progressCount: document.querySelector("#progress-count"),
        progressValue: document.querySelector("#progress-value"),
        previous: document.querySelector("#previous-button"),
        next: document.querySelector("#next-button"),
        result: document.querySelector("#result-view"),
        resultDescription: document.querySelector("#result-description"),
        resultOs: document.querySelector("#result-os-control"),
        specList: document.querySelector("#spec-list"),
        editSpec: document.querySelector("#edit-spec-button"),
        productButton: document.querySelector("#product-button"),
        searchLoadingView: document.querySelector("#search-loading-view"),
        searchLoadingSpecs: document.querySelector("#search-loading-specs"),
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
        cpuResetByOs: false,
        calculation: null,
        recommendationSource: null,
        finalSpecs: new Map(),
        resultViewMode: "BOTH",
        editMode: false,
        activeView: VIEW.INTRO,
        matchedProducts: [],
        productListMode: "MATCHED",
        productListReturnView: VIEW.RESULT,
        selectedProductId: null,
        searchLoadingTimer: null,
        lastViewedQuestionId: null,
        submittedFeedback: new Set()
    };

    renderInitialBaselines();
    elements.navProductList.addEventListener("click", showAllProducts);
    elements.start.addEventListener("click", startRecommendation);
    elements.baselineProduct.addEventListener("click", showBaselineRecommendation);
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
        card.querySelector(".eyebrow").textContent = "기본 권장 사양";
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
        renderNextButton(question);
        elements.questionContent.replaceChildren(
            question.kind === "current-spec" ? createCurrentSpecContent(question) : createQuestionContent(question)
        );
        if (state.lastViewedQuestionId !== question.id) {
            state.lastViewedQuestionId = question.id;
            recordEvent("QUESTION_VIEWED", {questionId: question.id});
        }
    }

    // 답을 고르지 않아도 건너뛸 수 있다. 미응답 답변은 정책에서 기본값으로 처리된다.
    function renderNextButton(question) {
        const isLastQuestion = state.questionIndex === state.visibleQuestions.length - 1;
        const skipping = !hasAnyAnswer(question);
        elements.next.textContent = skipping ? "건너뛰기" : (isLastQuestion ? "결과 보기" : "다음");
        elements.next.classList.toggle("button--primary", !skipping);
        elements.next.classList.toggle("button--skip", skipping);
    }

    function createCurrentSpecContent(question) {
        const fragment = createQuestionHeading(question);
        const form = document.createElement("div");
        form.className = "current-spec-form";
        form.append(
            createFieldMessage("choice-hint", "선택한 항목을 한 번 더 누르면 해제됩니다."),
            createChoiceField("CURRENT_OS", "OS", [
                [policy.OS.MACOS, "Mac"],
                [policy.OS.WINDOWS, "Windows"]
            ], state.currentSpec.os, updateCurrentOs),
            createChoiceField("CURRENT_CPU", "PROCESSOR", currentCpuOptions(), state.currentSpec.cpuTier, updateCurrentCpu, {
                disabled: !state.currentSpec.os,
                disabledMessage: "OS를 먼저 선택해 주세요",
                notice: state.cpuResetByOs ? "OS가 바뀌어 CPU 선택을 지웠어요. 다시 골라 주세요." : null
            }),
            createChoiceField("CURRENT_MEMORY", "MEMORY", [
                [8, "8GB 이하"], [16, "16GB"], [24, "24GB"], [32, "32GB"], [64, "64GB 이상"]
            ], state.currentSpec.memoryGb, (value) => updateCurrentNumber("memoryGb", "CURRENT_MEMORY", value)),
            createChoiceField("CURRENT_STORAGE", "STORAGE", [
                [256, "256GB 이하"], [512, "256GB 초과 1TB 미만"], [1024, "1TB 이상"]
            ], state.currentSpec.storageGb, (value) => updateCurrentNumber("storageGb", "CURRENT_STORAGE", value))
        );
        fragment.appendChild(form);
        return fragment;
    }

    function createChoiceField(id, label, options, value, onChange, config = {}) {
        const field = document.createElement("fieldset");
        field.className = "current-spec-field";
        const legend = document.createElement("legend");
        legend.textContent = label;
        field.append(legend);

        if (config.disabled) {
            field.appendChild(createFieldMessage("choice-notice", config.disabledMessage));
            return field;
        }
        if (config.notice) {
            field.appendChild(createFieldMessage("choice-notice", config.notice));
        }

        const group = createChoiceGroup(options, value, onChange);
        group.id = id;
        field.appendChild(group);
        if (config.hint) {
            field.appendChild(createFieldMessage("choice-hint", config.hint));
        }
        return field;
    }

    // 선택된 칩을 다시 누르면 해제된다. 사전 입력 네 항목이 모두 선택 사항이라 되돌릴 경로가 필요하다.
    function createChoiceGroup(options, value, onChange) {
        const group = document.createElement("div");
        group.className = "choice-group";
        options.forEach(([optionValue, optionLabel], index) => {
            const chip = document.createElement("button");
            chip.type = "button";
            chip.className = "chip";
            chip.textContent = optionLabel;
            const selected = value != null && String(value) === String(optionValue);
            chip.setAttribute("aria-pressed", String(selected));
            chip.addEventListener("click", () => {
                onChange(selected ? null : String(optionValue));
                // 선택하면 화면을 다시 그려 칩이 교체된다. 키보드 사용자가 자리를 잃지 않게 되돌린다.
                document.querySelectorAll(`#${group.id} .chip`)[index]?.focus();
            });
            group.appendChild(chip);
        });
        return group;
    }

    function createFieldMessage(className, text) {
        const message = document.createElement("p");
        message.className = className;
        message.textContent = text;
        return message;
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
            state.cpuResetByOs = true;
            recordEvent("ANSWER_CHANGED", {questionId: "CURRENT_CPU", optionId: null});
        }
        recordEvent(previous ? "ANSWER_CHANGED" : "QUESTION_ANSWERED", {questionId: "CURRENT_OS", optionId: value});
        renderQuestion();
    }

    function updateCurrentCpu(value) {
        state.cpuResetByOs = false;
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
        // 칩은 aria-pressed로만 선택을 표시한다. 다시 그리지 않으면 눌러도 반응이 없어 보인다.
        renderQuestion();
    }

    function createQuestionContent(question) {
        const fragment = createQuestionHeading(question);
        fragment.appendChild(createFieldMessage("choice-hint", "선택한 항목을 한 번 더 누르면 해제됩니다."));
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
        } else if (previous === optionId) {
            // 같은 옵션을 다시 누르면 해제한다. 사전 입력 칩·다중 선택은 이미 토글되므로 단일 선택도 맞춘다.
            state.answers.delete(answerKey);
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
            optionId: Array.isArray(current) ? current.join(",") : (current == null ? null : String(current))
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

    function isGroupAnswered(question, group) {
        const answer = state.answers.get(group.id || question.id);
        return Array.isArray(answer) ? answer.length > 0 : answer != null;
    }

    // 버튼 라벨용. 한 그룹이라도 답했으면 건너뛰는 게 아니므로 「다음」으로 보여준다.
    function hasAnyAnswer(question) {
        if (question.kind === "current-spec") {
            return policy.currentSpecSubmissionStatus(state.currentSpec) !== "ALL_SKIPPED";
        }
        return question.groups.some((group) => isGroupAnswered(question, group));
    }

    // QUESTION_SKIPPED 판정용. 라벨과 달리 모든 그룹을 답해야 완료로 본다.
    function isQuestionComplete(question) {
        // 현재 사양은 CURRENT_SPEC_SUBMITTED가 ALL_SKIPPED까지 기록하므로 QUESTION_SKIPPED를 따로 보내지 않는다.
        if (question.kind === "current-spec") {
            return true;
        }
        return question.groups.every((group) => isGroupAnswered(question, group));
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
            // 건너뛰기는 experiment_event ENUM에 없어 PostHog에만 보낸다.
            window.posthog?.capture("QUESTION_SKIPPED", {
                questionId: question.id,
                sessionId: state.sessionId,
                questionSetVersion: config.version
            });
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
        state.recommendationSource = "ONBOARDING";
        finalizeRecommendation();
    }

    function showBaselineRecommendation() {
        state.calculation = config.createBaselineRecommendation();
        state.recommendationSource = "BASELINE";
        finalizeRecommendation();
    }

    function finalizeRecommendation() {
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
        elements.resultDescription.textContent = state.recommendationSource === "BASELINE"
            ? "백엔드 개발용 기본 권장 사양입니다. 필요에 따라 직접 수정할 수 있어요."
            : "사용자님의 개발 환경과 답변을 바탕으로 조정된 권장 사양입니다.";
        renderResultOsControl();
        const osList = visibleResultOs();
        elements.specList.replaceChildren(...osList.map(createSpecCard));
        elements.editSpec.textContent = state.editMode ? "수정 완료" : "사양 직접 수정";
        const productSetReady = isProductSetReady();
        elements.productButton.disabled = !productSetReady;
        elements.productButton.textContent = productSetReady
            ? "확정한 사양으로 제품 검색"
            : "제품 목록 승인 대기";
    }

    function renderResultOsControl() {
        const options = [[policy.OS.MACOS, "Mac"], [policy.OS.WINDOWS, "Windows"], ["BOTH", "둘 다"]];
        const group = createChoiceGroup(options, state.resultViewMode, (value) => {
            if (value == null) {
                return;
            }
            state.resultViewMode = value;
            resetProductResults();
            renderSpecification();
            recordEvent("FINAL_SPEC_EDITED", {questionId: "RESULT_OS", optionId: value});
        });
        group.id = "result-os-select";
        group.classList.add("choice-group--segmented");
        elements.resultOs.replaceChildren(group);
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
        const group = createChoiceGroup(specOptions(os, field), value, (next) => {
            // 권장 사양은 항상 값이 있어야 하므로 해제는 받지 않는다.
            if (next != null) {
                updateFinalSpec(os, field, next);
            }
        });
        row.querySelector(".spec-row__value").replaceWith(group);
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

    function updateFinalSpec(os, field, rawValue) {
        const finalSpec = state.finalSpecs.get(os);
        const calculated = state.calculation.tracks[os].calculatedSpec[field];
        const next = field === "cpuTier" ? rawValue : Number(rawValue);
        // 취소하면 화면을 다시 그리지 않으므로 이전 선택이 그대로 남는다.
        if (isLower(os, field, next, calculated) && !window.confirm(loweringMessage(os, field))) {
            return;
        }
        finalSpec[field] = next;
        resetProductResults();
        renderSpecification();
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
        return `${fieldLabel(field)}: 권장값보다 낮은 값을 선택했습니다. ${expansion} 이 값으로 변경할까요?`;
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
        // 매칭은 순수 계산이라 미리 끝내 둔다. 지연 중에 실패할 여지를 남기지 않는다.
        state.matchedProducts = productPolicy.sortByPrice(visibleResultOs().flatMap((os) => {
            const spec = state.finalSpecs.get(os);
            return productPolicy.findMatches(config.products, spec, policy.CPU_TIERS);
        }));
        startSearchLoading();
    }

    function startSearchLoading() {
        renderSearchLoadingSpecs();
        activateView(VIEW.SEARCH_LOADING);
        scrollToViewStart();
        state.searchLoadingTimer = window.setTimeout(() => {
            state.searchLoadingTimer = null;
            openProductList();
        }, SEARCH_LOADING_MS);
    }

    function cancelSearchLoading() {
        if (state.searchLoadingTimer == null) {
            return;
        }
        window.clearTimeout(state.searchLoadingTimer);
        state.searchLoadingTimer = null;
    }

    // 검색 대상이 되는 사양을 OS별로 한 줄씩 보여준다. 표시 OS가 «둘 다»면 두 줄이 된다.
    function renderSearchLoadingSpecs() {
        elements.searchLoadingSpecs.replaceChildren(...visibleResultOs().map((os) => {
            const spec = state.finalSpecs.get(os);
            const line = document.createElement("p");
            line.className = "search-loading__spec";
            // CPU 라벨 안에 «/»가 들어가는 경우가 있어(«… 258V / … 445급») 구분자는 «·»를 쓴다.
            line.textContent = [
                policy.cpuLabel(os, spec.cpuTier),
                `${spec.memoryGb}GB`,
                formatStorage(spec.storageGb),
                osLabel(os)
            ].join(" · ");
            return line;
        }));
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
        // eyebrow와 제목이 같은 말이 되지 않게 나눈다. 제목이 목록의 정체를, eyebrow가 상위 분류를 맡는다.
        elements.productListEyebrow.textContent = showsAll ? "제품 목록" : "조건에 맞는 제품";
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
        // 로딩 중에 다른 화면으로 빠져나가면 예약된 전환이 나중에 그 화면을 덮어쓴다.
        // 호출부마다 챙기지 않도록 여기 한 곳에서 끊는다.
        if (view !== VIEW.SEARCH_LOADING) {
            cancelSearchLoading();
        }
        state.activeView = view;
        [
            [elements.intro, VIEW.INTRO],
            [elements.question, VIEW.QUESTION],
            [elements.result, VIEW.RESULT],
            [elements.searchLoadingView, VIEW.SEARCH_LOADING],
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
