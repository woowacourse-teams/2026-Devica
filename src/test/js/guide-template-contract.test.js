const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const guideTemplate = fs.readFileSync(
    path.join(__dirname, "../../main/resources/templates/pages/guide/index.html"),
    "utf8"
);
const detailTemplate = fs.readFileSync(
    path.join(__dirname, "../../main/resources/templates/pages/guide/why-24gb-512gb.html"),
    "utf8"
);
const criteriaTemplate = fs.readFileSync(
    path.join(__dirname, "../../main/resources/templates/pages/guide/what-to-consider.html"),
    "utf8"
);
const adjustTemplate = fs.readFileSync(
    path.join(__dirname, "../../main/resources/templates/pages/guide/adjust-for-your-needs.html"),
    "utf8"
);

test("가이드 메뉴는 외부 노션이 아닌 내부 가이드로 이동한다", () => {
    const siteFragment = fs.readFileSync(
        path.join(__dirname, "../../main/resources/templates/fragments/site.html"),
        "utf8"
    );

    assert.match(siteFragment, /th:href="@\{\/guide}"[^>]*>가이드<\/a>/);
    assert.doesNotMatch(siteFragment, /app\.notion\.com/);
    assert.doesNotMatch(siteFragment, /target="_blank"/);
});

test("가이드 초기 화면은 기본 권장 사양과 안내 문구를 모두 표시한다", () => {
    [
        "노트북 선택 가이드",
        "백엔드 개발자용",
        "먼저 결론부터",
        "24GB",
        "512GB",
        "M2 이후 세대, 기본 칩으로 충분",
        "Intel Core Ultra 7 또는 AMD Ryzen 7 이상",
        "OS 제안",
        "Mac",
        "위 사양은 정답이 아니라 하나의 제안입니다."
    ].forEach((content) => assert.ok(guideTemplate.includes(content), `${content} 문구가 있어야 합니다.`));
});

test("세 상세 가이드 카드는 각각 내부 상세 페이지로 이동한다", () => {
    const topicSection = guideTemplate.match(/<div class="guide-topic-list">([\s\S]*?)<\/div>\s*<\/section>/)?.[1];

    assert.ok(topicSection, "상세 가이드 목록이 있어야 합니다.");
    assert.equal((topicSection.match(/class="guide-topic-card guide-topic-card--link"/g) || []).length, 3);
    assert.match(topicSection, /th:href="@\{\/guide\/why-24gb-512gb}"/);
    assert.match(topicSection, /th:href="@\{\/guide\/what-to-consider}"/);
    assert.match(topicSection, /th:href="@\{\/guide\/adjust-for-your-needs}"/);
    assert.equal((topicSection.match(/<article class="guide-topic-card">/g) || []).length, 0);
    assert.match(topicSection, /왜 24GB · 512GB인가/);
    assert.match(topicSection, /무엇을 기준으로 봐야 하나/);
    assert.match(topicSection, /내 상황에 맞게 조정하기/);

    const inactiveCards = topicSection.match(/<article class="guide-topic-card">[\s\S]*?<\/article>/g) || [];
    assert.equal(inactiveCards.length, 0);
    inactiveCards.forEach((card) => assert.doesNotMatch(card, /<a\b|<button\b|href=|onclick=/));
});

test("뒤로 가기 버튼은 브라우저의 직전 페이지로 이동한다", () => {
    const guideApp = fs.readFileSync(
        path.join(__dirname, "../../main/resources/static/js/guide-app.js"),
        "utf8"
    );

    assert.match(guideTemplate, /id="guide-back-button"[^>]*>← 뒤로 가기<\/button>/);
    assert.match(guideApp, /window\.history\.back\(\)/);
});

test("상세 페이지는 가이드 이동 경로와 원문 제목을 표시한다", () => {
    assert.match(detailTemplate, /노트북 선택 가이드<\/a>/);
    assert.match(detailTemplate, /aria-current="page">왜 24GB · 512GB인가<\/span>/);
    assert.match(detailTemplate, /<h1>왜 24GB · 512GB인가<\/h1>/);
    assert.ok(detailTemplate.includes("24GB와 512GB라는 권장 사양이 어떤 근거로 나왔는지, 메모리·저장 공간·CPU·OS 기준으로 살펴봅니다."));
    assert.ok(detailTemplate.includes("이 페이지에서는 24GB, 512GB 같은 값이 어떻게 나왔는지 항목별로 짚어 본다."));
    assert.match(detailTemplate, /<a class="guide-back-button" th:href="@\{\/guide}"/);
    assert.match(detailTemplate, /th:href="@\{\/guide}"[^>]*>노트북 선택 가이드로 돌아가기<\/a>/);
});

test("상세 페이지는 원문의 다섯 섹션과 참고 자료를 순서대로 표시한다", () => {
    const headings = [
        "1. 메모리, 왜 24GB인가",
        "2. 저장 공간, 왜 512GB인가",
        "3. CPU(Mac), 왜 기본 칩부터인가",
        "4. CPU(Windows), 왜 Core Ultra 7 · Ryzen 7 이상인가",
        "5. OS, 왜 Mac을 제안하나",
        "참고 자료"
    ];

    let previousIndex = -1;
    headings.forEach((heading) => {
        const currentIndex = detailTemplate.indexOf(heading);
        assert.ok(currentIndex > previousIndex, `${heading}이 원문 순서대로 있어야 합니다.`);
        previousIndex = currentIndex;
    });
});

test("상세 페이지는 아홉 개 표의 모든 행과 수치를 표시한다", () => {
    assert.equal((detailTemplate.match(/<table>/g) || []).length, 9);

    [
        ["운영체제와 브라우저", "5~9GB"],
        ["IDE", "1~5GB"],
        ["Docker", "5~6GB"],
        ["캐시(Redis), Go·Node 애플리케이션", "0.1~0.3GB"],
        ["데이터베이스(PostgreSQL, MySQL, MongoDB), JVM 애플리케이션", "0.5~1GB"],
        ["검색·메시징(Elasticsearch, Kafka)", "1~4GB"],
        ["VS Code + 데이터베이스 두 개", "12~19GB"],
        ["IntelliJ + JVM 애플리케이션 + Elasticsearch", "15~25GB"],
        ["운영체제", "40~60GB"],
        ["Docker Desktop과 베이스 VM", "6GB"],
        ["IDE와 언어별 런타임", "10~25GB"],
        ["프로젝트 폴더 안", "하나당 2~10GB"],
        ["홈 디렉터리의 캐시 폴더", "5~20GB"],
        ["Docker", "20~40GB"],
        ["컨테이너 구동, 코드 편집, 애플리케이션 기동", "코어 하나의 속도"],
        ["빌드, 테스트 전체 실행", "코어 개수"],
        ["M1 기본", "16GB"],
        ["M2 · M3 기본", "24GB"],
        ["M4 이후 기본", "32GB"],
        ["Pro 등급", "세대에 따라 36~64GB"],
        ["U", "저전력. 얇고 가벼운 모델에 들어간다"],
        ["H · HX", "고성능. 발열을 감수하고 성능을 낸다"],
        ["개발자 전체", "39.1%", "52.2%"],
        ["백엔드", "42.2%", "41.5%"],
        ["백엔드 중 Docker 사용자", "46.8%", "37.0%"]
    ].forEach((row) => row.forEach((cell) => assert.ok(detailTemplate.includes(cell), `${cell} 표 데이터가 있어야 합니다.`)));
});

test("상세 페이지는 원문 인용문과 인라인 코드를 유지한다", () => {
    assert.match(detailTemplate, /<blockquote class="guide-note">/);
    assert.ok(detailTemplate.includes("이 절의 메모리 수치는 공식 문서로 확인되는 값이 아니라 일반적인 사용 환경에서의 어림값이다. 정확한 값은 구성에 따라 달라진다."));

    const inlineCodes = [...detailTemplate.matchAll(/<code>(.*?)<\/code>/g)].map((match) => match[1]);
    assert.deepEqual(inlineCodes, ["docker stats", "node_modules", "~/.gradle", "~/.npm", "docker system prune"]);
});

test("상세 페이지는 원문의 외부 링크를 모두 유지한다", () => {
    const externalLinks = [...detailTemplate.matchAll(/href="(https:\/\/[^\"]+)"/g)].map((match) => match[1]);
    assert.equal(externalLinks.length, 12);
    assert.deepEqual(new Set(externalLinks), new Set([
        "https://learn.microsoft.com/en-us/windows/whats-new/windows-11-requirements",
        "https://docs.docker.com/desktop/setup/install/windows-install/",
        "https://survey.stackoverflow.co/2025/",
        "https://www.apple.com/newsroom/2020/11/introducing-the-next-generation-of-mac/",
        "https://lp.jetbrains.com/the-state-of-java-2025/"
    ]));
    assert.doesNotMatch(detailTemplate, /target="_blank"/);
});

test("선택 기준 상세 페이지는 가이드 이동 경로와 원문 도입을 표시한다", () => {
    assert.match(criteriaTemplate, /노트북 선택 가이드<\/a>/);
    assert.match(criteriaTemplate, /aria-current="page">무엇을 기준으로 봐야 하나<\/span>/);
    assert.match(criteriaTemplate, /<h1>무엇을 기준으로 봐야 하나<\/h1>/);
    assert.ok(criteriaTemplate.includes("노트북 사양 가운데 백엔드 개발에 실제로 영향을 주고, 구매 후 바꾸기 어려운 항목을 기준으로 정리합니다."));
    assert.ok(criteriaTemplate.includes("노트북 스펙표에는 볼 것이 많다. 그중 메모리, 저장 공간, CPU 세 가지만 남겼다."));
    assert.ok(criteriaTemplate.includes("백엔드 작업에서 실제로 병목이 되는가"));
    assert.ok(criteriaTemplate.includes("살 때 정하면 나중에 바꾸기 어려운가"));
    assert.match(criteriaTemplate, /<a class="guide-back-button" th:href="@\{\/guide}"/);
    assert.match(criteriaTemplate, /th:href="@\{\/guide}"[^>]*>노트북 선택 가이드로 돌아가기<\/a>/);
});

test("선택 기준 상세 페이지는 원문 섹션을 순서대로 표시한다", () => {
    const headings = [
        "1. 메모리",
        "2. 저장 공간",
        "3. CPU",
        "이 셋에 들지 못한 것",
        "왜 백엔드 기준인가",
        "언어별로도 나눠야 하지 않나",
        "이 기준의 한계",
        "참고 자료"
    ];

    let previousIndex = -1;
    headings.forEach((heading) => {
        const currentIndex = criteriaTemplate.indexOf(heading);
        assert.ok(currentIndex > previousIndex, `${heading}이 원문 순서대로 있어야 합니다.`);
        previousIndex = currentIndex;
    });

    [
        "IDE와 Docker, 데이터베이스, 내가 만든 서비스가 함께 떠 있다.",
        "물리 메모리를 넘기면 스왑이 시작되면서 전체가 함께 느려진다.",
        "Mac은 내장 SSD를 늘릴 방법이 없다.",
        "칩 등급이 곧 살 수 있는 메모리 상한이라, CPU를 고르는 일이 메모리를 고르는 일이 된다.",
        "독립 그래픽카드",
        "고주사율 화면",
        "배터리 시간",
        "화면 크기와 무게",
        "JavaScript 56.3%, Python 53.9%, Java 36.5%, Go 24.4%",
        "권장 사양은 가장 흔한 경우를 잡은 값"
    ].forEach((content) => assert.ok(criteriaTemplate.includes(content), `${content} 원문 내용이 있어야 합니다.`));
});

test("선택 기준 상세 페이지는 백엔드와 프론트엔드 비교표를 모두 표시한다", () => {
    assert.equal((criteriaTemplate.match(/<table>/g) || []).length, 1);
    [
        ["Docker", "80.8%", "59.9%"],
        ["PostgreSQL", "64.1%", "48.9%"],
        ["Kubernetes", "43.8%", "14.5%"],
        ["Redis", "42.0%", "15.2%"],
        ["Elasticsearch", "23.6%", "9.7%"]
    ].forEach((row) => row.forEach((cell) => assert.ok(criteriaTemplate.includes(cell), `${cell} 비교표 데이터가 있어야 합니다.`)));
});

test("선택 기준 상세 페이지는 원문의 참고 링크 두 곳을 유지한다", () => {
    const externalLinks = [...criteriaTemplate.matchAll(/href="(https:\/\/[^\"]+)"/g)].map((match) => match[1]);
    assert.deepEqual(externalLinks, [
        "https://survey.stackoverflow.co/2025/",
        "https://survey.stackoverflow.co/2025/"
    ]);
    assert.ok(criteriaTemplate.includes("도구 사용률, 데이터베이스 개수, 언어 분포. 원본 응답 데이터를 직업 개발자 기준으로 집계했다. 복수 응답 문항이라 합이 100%를 넘는다."));
    assert.doesNotMatch(criteriaTemplate, /target="_blank"/);
});

test("상황별 조정 상세 페이지는 가이드 이동 경로와 원문 도입을 표시한다", () => {
    assert.match(adjustTemplate, /노트북 선택 가이드<\/a>/);
    assert.match(adjustTemplate, /aria-current="page">내 상황에 맞게 조정하기<\/span>/);
    assert.match(adjustTemplate, /<h1>내 상황에 맞게 조정하기<\/h1>/);
    assert.ok(adjustTemplate.includes("기본 권장 사양을 출발점으로 삼아, 개발 환경·예산·사용 방식에 맞게 사양을 올리거나 낮추는 기준을 설명합니다."));
    assert.ok(adjustTemplate.includes("권장 사양은 가장 흔한 경우를 잡은 값이다. 조건이 다르면 답도 달라진다."));
    assert.ok(adjustTemplate.includes("이 페이지는 조정 기능이 어떤 근거로 사양을 올리고 내리는지 설명한다."));
    assert.match(adjustTemplate, /<a class="guide-back-button" th:href="@\{\/guide}"/);
    assert.match(adjustTemplate, /th:href="@\{\/guide}"[^>]*>노트북 선택 가이드로 돌아가기<\/a>/);
});

test("상황별 조정 상세 페이지는 원문 여섯 섹션을 순서대로 표시한다", () => {
    const headings = [
        "모든 판단의 출발점: 막히느냐, 느려지느냐",
        "메모리 — 동시에 켜두는 개수가 결정한다",
        "저장 공간 — 도커 이미지가 변수다",
        "CPU — 등급보다 사용 패턴을 본다",
        "OS — 조정 대상이라기보다 취향에 가깝다",
        "되돌릴 수 있는 것과 없는 것"
    ];

    let previousIndex = -1;
    headings.forEach((heading) => {
        const currentIndex = adjustTemplate.indexOf(heading);
        assert.ok(currentIndex > previousIndex, `${heading}이 원문 순서대로 있어야 합니다.`);
        previousIndex = currentIndex;
    });

    [
        "메모리가 부족하면 스왑이 시작되면서 전체가 함께 느려진다.",
        "도커로 DB와 캐시, 메시지 브로커를 함께 띄우거나",
        "개발 환경 자체를 원격 서버나 클라우드에 두는 경우다.",
        "문서와 코드만으로는 256GB도 남는다. 문제는 도커다.",
        "영상 편집이나 로컬 AI 모델 실행처럼 개발 외의 무거운 작업",
        "세대(M2→M3→M4→M5)",
        "등급(기본→Pro→Max)",
        "맥은 램도 저장 공간도 살 때 정하는 게 마지막이다.",
        "윈도우는 M.2 슬롯이 있는 모델이라면 SSD를 교체하거나 추가할 수 있다."
    ].forEach((content) => assert.ok(adjustTemplate.includes(content), `${content} 원문 내용이 있어야 합니다.`));
});

test("상황별 조정 상세 페이지는 원문의 목록과 안내 인용문을 유지한다", () => {
    assert.equal((adjustTemplate.match(/<ul class="guide-detail-points">/g) || []).length, 3);
    assert.equal((adjustTemplate.match(/<li>/g) || []).length, 8);
    assert.ok(adjustTemplate.includes("모자라면 작업이 막히는 항목 — 메모리, 저장 공간"));
    assert.ok(adjustTemplate.includes("모자라면 느려지는 항목 — CPU"));
    assert.ok(adjustTemplate.includes("같은 가격이면 램과 저장 공간을 더 준다."));
    assert.ok(adjustTemplate.includes("게임을 하거나, C#/.NET처럼 윈도우 쪽 기술을 다룰 계획이다."));

    assert.equal((adjustTemplate.match(/<blockquote class="guide-note">/g) || []).length, 2);
    assert.ok(adjustTemplate.includes("윈도우에서 도커를 쓸 계획이라면 32GB를 고려할 만하다."));
    assert.ok(adjustTemplate.includes("윈도우 CPU는 이름 끝의 알파벳도 함께 본다."));
});

test("상황별 조정 상세 페이지는 원문에 없는 외부 링크를 추가하지 않고 기존 근거 페이지만 연결한다", () => {
    assert.doesNotMatch(adjustTemplate, /href="https:\/\//);
    assert.match(adjustTemplate, /th:href="@\{\/guide\/why-24gb-512gb}"[^>]*>「왜 24GB · 512GB인가」<\/a>/);
    assert.doesNotMatch(adjustTemplate, /target="_blank"/);
});

test("헤더의 제품 목록은 어느 페이지에서든 이동할 수 있는 링크다", () => {
    const siteFragment = fs.readFileSync(
        path.join(__dirname, "../../main/resources/templates/fragments/site.html"),
        "utf8"
    );
    const probeApp = fs.readFileSync(
        path.join(__dirname, "../../main/resources/static/js/probe-app.js"),
        "utf8"
    );

    assert.match(siteFragment, /id="nav-product-list-button" th:href="@\{\/\(view=products\)}">제품 목록<\/a>/);
    assert.match(probeApp, /view"\) === "products"/);
});
