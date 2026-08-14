(() => {
    "use strict";

    const config = window.DEVICA_PROBE_CONFIG;
    if (!config || !config.approved) {
        return;
    }

    const elements = {
        intro: document.querySelector("#intro-view"),
        start: document.querySelector("#start-button"),
        question: document.querySelector("#question-view"),
        questionContent: document.querySelector("#question-content"),
        progressCount: document.querySelector("#progress-count"),
        progressValue: document.querySelector("#progress-value"),
        previous: document.querySelector("#previous-button"),
        next: document.querySelector("#next-button"),
        result: document.querySelector("#result-view"),
        specList: document.querySelector("#spec-list"),
        editSpec: document.querySelector("#edit-spec-button"),
        productButton: document.querySelector("#product-button"),
        product: document.querySelector("#product-view"),
        productList: document.querySelector("#product-list"),
        feedback: document.querySelector("#feedback-view"),
        feedbackComplete: document.querySelector("#feedback-complete")
    };

    const state = {
        sessionId: getSessionId(),
        questionIndex: 0,
        visibleQuestions: [],
        answers: new Map(),
        recommendation: null,
        submittedFeedback: new Set()
    };

    elements.start.addEventListener("click", startRecommendation);
    elements.previous.addEventListener("click", showPreviousQuestion);
    elements.next.addEventListener("click", showNextQuestion);
    elements.editSpec.addEventListener("click", editSpecification);
    elements.productButton.addEventListener("click", showProducts);
    document.querySelectorAll("[data-feedback]").forEach(bindFeedbackGroup);

    function startRecommendation() {
        state.visibleQuestions = resolveVisibleQuestions();
        state.questionIndex = 0;
        elements.intro.classList.add("is-hidden");
        elements.question.classList.remove("is-hidden");
        recordEvent("RECOMMENDATION_STARTED");
        renderQuestion();
    }

    function resolveVisibleQuestions() {
        return config.questions.filter((question) => {
            if (typeof question.isVisible !== "function") {
                return true;
            }
            return question.isVisible(Object.fromEntries(state.answers));
        });
    }

    function renderQuestion() {
        state.visibleQuestions = resolveVisibleQuestions();
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
        elements.questionContent.replaceChildren(createQuestionContent(question));
        recordEvent("QUESTION_VIEWED", {questionId: question.id});
    }

    function createQuestionContent(question) {
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

        question.groups.forEach((group) => {
            const groupContainer = document.createElement("div");
            groupContainer.className = "question-group";
            group.options.forEach((option) => groupContainer.appendChild(createOption(question, group, option)));
            fragment.appendChild(groupContainer);
        });
        return fragment;
    }

    function createOption(question, group, option) {
        const button = document.createElement("button");
        const answerKey = group.id || question.id;
        button.type = "button";
        button.className = "question-option";
        button.setAttribute("aria-pressed", String(state.answers.get(answerKey) === option.id));

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

        button.addEventListener("click", () => selectOption(question, answerKey, option.id));
        return button;
    }

    function selectOption(question, answerKey, optionId) {
        const previousOption = state.answers.get(answerKey);
        state.answers.set(answerKey, optionId);
        recordEvent(previousOption ? "ANSWER_CHANGED" : "QUESTION_ANSWERED", {
            questionId: answerKey,
            optionId
        });
        renderQuestion();
    }

    function isQuestionComplete(question) {
        return question.groups.every((group) => state.answers.has(group.id || question.id));
    }

    function showPreviousQuestion() {
        if (state.questionIndex === 0) {
            return;
        }
        state.questionIndex -= 1;
        renderQuestion();
    }

    function showNextQuestion() {
        const question = state.visibleQuestions[state.questionIndex];
        if (!isQuestionComplete(question)) {
            return;
        }
        if (state.questionIndex >= state.visibleQuestions.length - 1) {
            completeRecommendation();
            return;
        }
        state.questionIndex += 1;
        renderQuestion();
    }

    function completeRecommendation() {
        state.recommendation = config.calculateRecommendation(Object.fromEntries(state.answers));
        elements.question.classList.add("is-hidden");
        elements.result.classList.remove("is-hidden");
        elements.feedback.classList.remove("is-hidden");
        renderSpecification();
        recordEvent("RECOMMENDATION_COMPLETED");
    }

    function renderSpecification() {
        elements.specList.replaceChildren(...state.recommendation.specs.map((spec) => {
            const row = document.createElement("article");
            row.className = "spec-row";
            row.innerHTML = `<span class="spec-row__label"></span><strong class="spec-row__value"></strong><p class="spec-row__reason"></p>`;
            row.querySelector(".spec-row__label").textContent = spec.label;
            row.querySelector(".spec-row__value").textContent = spec.value;
            row.querySelector(".spec-row__reason").textContent = spec.reason;
            return row;
        }));
    }

    function editSpecification() {
        recordEvent("FINAL_SPEC_EDITED");
        state.questionIndex = 0;
        elements.result.classList.add("is-hidden");
        elements.product.classList.add("is-hidden");
        elements.question.classList.remove("is-hidden");
        renderQuestion();
    }

    function showProducts() {
        elements.product.classList.remove("is-hidden");
        elements.productList.replaceChildren(...config.products.map(createProductCard));
        recordEvent("PRODUCT_LIST_VIEWED");
        elements.product.scrollIntoView({behavior: "smooth"});
    }

    function createProductCard(product) {
        const card = document.createElement("article");
        card.className = "product-card";
        card.innerHTML = `<span class="product-card__maker"></span><h3></h3><p></p><a class="button button--primary button--wide" target="_blank" rel="noopener noreferrer">제품 확인</a>`;
        card.querySelector(".product-card__maker").textContent = product.maker;
        card.querySelector("h3").textContent = product.name;
        card.querySelector("p").textContent = product.summary;
        const link = card.querySelector("a");
        link.href = product.url;
        link.addEventListener("click", () => recordEvent("PRODUCT_CLICKED", {optionId: product.id}));
        return card;
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
        const request = {
            sessionId: state.sessionId,
            questionSetVersion: config.version,
            eventName,
            questionId: details.questionId || null,
            optionId: details.optionId || null,
            recommendationSnapshot: state.recommendation ? state.recommendation.snapshot : null,
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
