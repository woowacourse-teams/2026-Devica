(() => {
    "use strict";

    const policy = window.DevicaProbePolicy;
    const productPolicy = window.DevicaProductPolicy;
    const products = Object.freeze([
        product({
            id: "mac-air13-m5-24-1t",
            brand: "Apple",
            modelName: "MacBook Air 13 M5",
            modelCode: "MDHD4KH/A",
            os: "MACOS",
            cpuManufacturer: "Apple",
            cpuModelName: "Apple M5 10코어 CPU·10코어 GPU",
            cpuTier: "BASIC",
            memoryGb: 24,
            storageGb: 1024,
            priceKrw: 2840000,
            purchaseUrl: "https://www.coupang.com/vp/products/9410641464?itemId=27961674159&vendorItemId=94919713484",
            shortDescription: "Apple M5와 24GB 메모리, 1TB SSD를 갖춘 13형 MacBook Air입니다."
        }),
        product({
            id: "mac-air15-m5-24-1t",
            brand: "Apple",
            modelName: "MacBook Air 15 M5",
            modelCode: "MDVC4KH/A",
            os: "MACOS",
            cpuManufacturer: "Apple",
            cpuModelName: "Apple M5 10코어 CPU·10코어 GPU",
            cpuTier: "BASIC",
            memoryGb: 24,
            storageGb: 1024,
            priceKrw: 3126240,
            purchaseUrl: "https://www.coupang.com/vp/products/9410641499?itemId=27961674335&vendorItemId=94919713465",
            shortDescription: "Apple M5와 24GB 메모리, 1TB SSD를 갖춘 15형 MacBook Air입니다."
        }),
        product({
            id: "mac-pro14-m5-24-1t",
            brand: "Apple",
            modelName: "MacBook Pro 14 M5",
            modelCode: "Z1KN0001B",
            os: "MACOS",
            cpuManufacturer: "Apple",
            cpuModelName: "Apple M5 10코어 CPU·10코어 GPU",
            cpuTier: "BASIC",
            memoryGb: 24,
            storageGb: 1024,
            priceKrw: 3321990,
            purchaseUrl: "https://www.coupang.com/vp/products/9140128274?itemId=27033962329&vendorItemId=94002473679",
            shortDescription: "Apple M5와 24GB 메모리, 1TB SSD를 갖춘 14형 MacBook Pro입니다."
        }),
        product({
            id: "mac-pro14-m5pro-24-1t",
            brand: "Apple",
            modelName: "MacBook Pro 14 M5 Pro",
            modelCode: "Z1MH001VY",
            os: "MACOS",
            cpuManufacturer: "Apple",
            cpuModelName: "Apple M5 Pro 15코어 CPU·16코어 GPU",
            cpuTier: "PRO",
            memoryGb: 24,
            storageGb: 1024,
            priceKrw: 3826900,
            purchaseUrl: "https://www.coupang.com/vp/products/9140128274?itemId=28078100250&vendorItemId=95034602880",
            shortDescription: "Apple M5 Pro와 24GB 메모리, 1TB SSD를 갖춘 14형 MacBook Pro입니다."
        }),
        product({
            id: "mac-pro14-m5pro-48-1t",
            brand: "Apple",
            modelName: "MacBook Pro 14 M5 Pro 48GB",
            modelCode: "Z1ML001Y3",
            os: "MACOS",
            cpuManufacturer: "Apple",
            cpuModelName: "Apple M5 Pro 15코어 CPU·16코어 GPU",
            cpuTier: "PRO",
            memoryGb: 48,
            storageGb: 1024,
            priceKrw: 4846900,
            purchaseUrl: "https://www.coupang.com/vp/products/9140128274?itemId=28093741944&vendorItemId=95050035786",
            shortDescription: "Apple M5 Pro와 48GB 메모리, 1TB SSD를 갖춘 14형 MacBook Pro입니다."
        }),
        product({
            id: "mac-pro14-m5pro-48-2t",
            brand: "Apple",
            modelName: "MacBook Pro 14 M5 Pro 48GB 2TB",
            modelCode: "Z1MM001CH",
            os: "MACOS",
            cpuManufacturer: "Apple",
            cpuModelName: "Apple M5 Pro 18코어 CPU·20코어 GPU",
            cpuTier: "PRO",
            memoryGb: 48,
            storageGb: 2048,
            priceKrw: 5961000,
            purchaseUrl: "https://www.coupang.com/vp/products/9140128274?itemId=28093741917&vendorItemId=95050035747",
            shortDescription: "Apple M5 Pro와 48GB 메모리, 2TB SSD를 갖춘 14형 MacBook Pro입니다."
        }),
        product({
            id: "mac-pro16-m5pro-48-1t",
            brand: "Apple",
            modelName: "MacBook Pro 16 M5 Pro",
            modelCode: "MGEC4KH/A",
            os: "MACOS",
            cpuManufacturer: "Apple",
            cpuModelName: "Apple M5 Pro 18코어 CPU·20코어 GPU",
            cpuTier: "PRO",
            memoryGb: 48,
            storageGb: 1024,
            priceKrw: 5566770,
            purchaseUrl: "https://www.coupang.com/vp/products/9410700446?itemId=27961799447&vendorItemId=94919837921",
            shortDescription: "Apple M5 Pro와 48GB 메모리, 1TB SSD를 갖춘 16형 MacBook Pro입니다."
        }),
        product({
            id: "mac-pro16-m5max-48-2t",
            brand: "Apple",
            modelName: "MacBook Pro 16 M5 Max",
            modelCode: "MGEE4KH/A",
            os: "MACOS",
            cpuManufacturer: "Apple",
            cpuModelName: "Apple M5 Max 18코어 CPU·40코어 GPU",
            cpuTier: "MAX",
            memoryGb: 48,
            storageGb: 2048,
            priceKrw: 7670000,
            purchaseUrl: "https://www.coupang.com/vp/products/9410700446?itemId=27961799450&vendorItemId=94919837915",
            shortDescription: "Apple M5 Max와 48GB 메모리, 2TB SSD를 갖춘 16형 MacBook Pro입니다."
        }),
        product({
            id: "win-samsung-book6-356h",
            brand: "Samsung",
            modelName: "Galaxy Book6",
            modelCode: "NT760VJT-A71A",
            os: "WINDOWS",
            cpuManufacturer: "Intel",
            cpuModelName: "Intel Core Ultra 7 356H",
            cpuTier: "H",
            memoryGb: 32,
            storageGb: 1024,
            priceKrw: 2849000,
            purchaseUrl: "https://www.coupang.com/vp/products/9458954410?itemId=28152507754&vendorItemId=95107817299",
            shortDescription: "Intel Core Ultra 7 356H와 32GB 메모리, 1TB SSD를 갖춘 Galaxy Book6입니다."
        }),
        product({
            id: "win-samsung-book6-ultra-356h",
            brand: "Samsung",
            modelName: "Galaxy Book6 Ultra",
            modelCode: "NT960UJH-X72A",
            os: "WINDOWS",
            cpuManufacturer: "Intel",
            cpuModelName: "Intel Core Ultra 7 356H",
            cpuTier: "H",
            memoryGb: 32,
            storageGb: 1024,
            priceKrw: 5829000,
            purchaseUrl: "https://www.coupang.com/vp/products/9339542914?itemId=27696774261&vendorItemId=94658532515",
            shortDescription: "Intel Core Ultra 7 356H와 32GB 메모리, 1TB SSD를 갖춘 Galaxy Book6 Ultra입니다."
        }),
        product({
            id: "win-lg-grampro16-258v",
            brand: "LG",
            modelName: "gram Pro AI 16",
            modelCode: "16Z90TS-GU7WK",
            os: "WINDOWS",
            cpuManufacturer: "Intel",
            cpuModelName: "Intel Core Ultra 7 258V",
            cpuTier: "P_HS",
            memoryGb: 32,
            storageGb: 1024,
            priceKrw: 3000000,
            purchaseUrl: "https://www.coupang.com/vp/products/8670821592?itemId=25170697413&vendorItemId=95514237961",
            shortDescription: "Intel Core Ultra 7 258V와 32GB 메모리, 1TB SSD를 갖춘 gram Pro AI 16입니다."
        }),
        product({
            id: "win-lg-grampro16-255h",
            brand: "LG",
            modelName: "gram Pro 16",
            modelCode: "16Z90TR-SD7WK",
            os: "WINDOWS",
            cpuManufacturer: "Intel",
            cpuModelName: "Intel Core Ultra 7 255H",
            cpuTier: "H",
            memoryGb: 32,
            storageGb: 1024,
            priceKrw: 2850000,
            purchaseUrl: "https://www.coupang.com/vp/products/8829567591?itemId=25726533506&vendorItemId=92715203892",
            shortDescription: "Intel Core Ultra 7 255H와 32GB 메모리, 1TB SSD를 갖춘 gram Pro 16입니다."
        }),
        product({
            id: "win-lenovo-ideapadpro5-356h",
            brand: "Lenovo",
            modelName: "IdeaPad Pro 5i Gen 11",
            modelCode: "83SK0009KR",
            os: "WINDOWS",
            cpuManufacturer: "Intel",
            cpuModelName: "Intel Core Ultra 7 356H",
            cpuTier: "H",
            memoryGb: 32,
            storageGb: 1024,
            priceKrw: 2449000,
            purchaseUrl: "https://www.coupang.com/vp/products/9478742459?itemId=28219563534&vendorItemId=95173538944",
            shortDescription: "Intel Core Ultra 7 356H와 32GB 메모리, 1TB SSD를 갖춘 IdeaPad Pro 5i입니다."
        }),
        product({
            id: "win-lenovo-legionpro5-275hx",
            brand: "Lenovo",
            modelName: "Legion Pro 5i Gen 10",
            modelCode: "83LU000UKR",
            os: "WINDOWS",
            cpuManufacturer: "Intel",
            cpuModelName: "Intel Core Ultra 9 275HX",
            cpuTier: "HX",
            memoryGb: 32,
            storageGb: 1024,
            priceKrw: 4649000,
            purchaseUrl: "https://www.coupang.com/vp/products/8888042529?itemId=25949552933&vendorItemId=92932462159",
            shortDescription: "Intel Core Ultra 9 275HX와 32GB 메모리, 1TB SSD를 갖춘 Legion Pro 5i입니다."
        }),
        product({
            id: "win-asus-zenbook14-ai7",
            brand: "ASUS",
            modelName: "Zenbook 14",
            modelCode: "UM3406GA-QL151W",
            os: "WINDOWS",
            cpuManufacturer: "AMD",
            cpuModelName: "AMD Ryzen AI 7 445",
            cpuTier: "P_HS",
            memoryGb: 32,
            storageGb: 1024,
            priceKrw: 2099000,
            purchaseUrl: "https://www.coupang.com/vp/products/9470833374?itemId=27653967019&vendorItemId=94616357403",
            shortDescription: "AMD Ryzen AI 7 445와 32GB 메모리, 1TB SSD를 갖춘 Zenbook 14입니다."
        }),
        product({
            id: "win-asus-rog-g16-8940hx",
            brand: "ASUS",
            modelName: "ROG Strix G16",
            modelCode: "G614PM-TS171W",
            os: "WINDOWS",
            cpuManufacturer: "AMD",
            cpuModelName: "AMD Ryzen 9 8940HX",
            cpuTier: "HX",
            memoryGb: 32,
            storageGb: 1024,
            priceKrw: 3814940,
            purchaseUrl: "https://www.coupang.com/vp/products/9568845900?itemId=28559191661&vendorItemId=95503812693",
            shortDescription: "AMD Ryzen 9 8940HX와 32GB 메모리, 1TB SSD를 갖춘 ROG Strix G16입니다."
        })
    ]);
    const productSetApproved = true;
    const productSetValidation = productPolicy.validateCatalog({
        products,
        approved: productSetApproved,
        cpuTiers: policy.CPU_TIERS
    });

    window.DEVICA_PROBE_CONFIG = Object.freeze({
        version: "question-set-v0.1",
        productSetVersion: "product-set-2026-08-14-v0.1",
        approved: true,
        productSetApproved,
        productSetValidation,
        baselineTracks: policy.BASELINES,
        questions: Object.freeze([
            {
                id: "CURRENT_SPEC",
                kind: "current-spec",
                title: "현재 사용하는 노트북 사양을 알려주세요",
                description: "모든 항목은 선택 사항입니다. 입력하지 않아도 다음 질문으로 진행할 수 있어요.",
                groups: []
            },
            singleQuestion("Q1", "어떤 노트북을 생각하고 계신가요?", [
                option("MAC", "맥"),
                option("WINDOWS", "윈도우"),
                option("UNDECIDED", "아직 안 정했다", "결과에서 Mac과 Windows 권장안을 함께 보여드려요.")
            ]),
            {
                id: "Q2",
                title: "주로 다루는 언어는 무엇인가요?",
                description: "여러 개를 선택할 수 있어요.",
                groups: [{
                    id: "Q2",
                    multiple: true,
                    exclusiveOptionId: "UNDECIDED",
                    options: [
                        option("JAVA_FAMILY", "Java, Kotlin, C# 등"),
                        option("NODE_TYPESCRIPT", "Node.js, TypeScript"),
                        option("OTHER_LIGHT", "Python, Go, PHP 등"),
                        option("UNDECIDED", "아직 정하지 않았다")
                    ]
                }]
            },
            {
                id: "Q3_SCREEN",
                title: "개발 도구를 알려주세요",
                groups: [
                    {
                        id: "Q3",
                        label: "Q3. 어떤 IDE를 쓰나요?",
                        options: [
                            option("JETBRAINS", "IntelliJ, PyCharm 등 JetBrains 제품"),
                            option("LIGHT_EDITOR", "VS Code, Vim, Neovim 등"),
                            option("MULTIPLE", "여러 개를 동시에 함께 쓴다"),
                            option("UNKNOWN", "아직 모르겠다")
                        ]
                    },
                    {
                        id: "Q3_1",
                        label: "Q3-1. AI 코딩 도구를 쓰나요?",
                        options: [
                            option("AI_EDITOR", "Cursor 같은 AI 전용 에디터를 쓴다"),
                            option("IDE_EXTENSION", "기존 IDE에 확장으로 붙여 쓴다"),
                            option("NONE", "안 쓴다")
                        ]
                    }
                ]
            },
            singleQuestion("Q4", "개발 환경을 어떻게 띄우나요?", [
                option("MULTIPLE_SERVICES", "로컬에 직접 설치해서 여러 개 띄운다"),
                option("LOCAL", "로컬에 직접 설치해서 한두 개 띄운다"),
                option("DOCKER_MANY", "도커로 여러 개를 한 번에 띄운다"),
                option("DOCKER_FEW", "도커로 한두 개 띄운다"),
                option("REMOTE", "원격 서버에 접속해서 쓴다"),
                option("NO_EXPERIENCE", "아직 다뤄본 적이 없다")
            ]),
            singleQuestion("Q5", "프로그램을 여러 개 켜두면 멈추거나 껐다 켜야 했나요?", [
                option("OFTEN", "자주 그렇다"),
                option("SOMETIMES", "가끔 느려진다"),
                option("FINE", "괜찮다"),
                option("UNKNOWN", "잘 모르겠다")
            ]),
            singleQuestion("Q6", "용량이 부족해서 뭔가를 지우거나 옮긴 적이 있나요?", [
                option("OFTEN", "자주 있다"),
                option("ONCE_OR_TWICE", "한두 번 있다"),
                option("NEVER", "없다")
            ]),
            {
                ...singleQuestion("Q7", "빌드나 테스트가 끝나기를 기다리는 게 답답했나요?", [
                    option("OFTEN", "자주 답답했다"),
                    option("FINE", "참을 만했다 / 신경 안 쓰였다"),
                    option("UNKNOWN", "잘 모르겠다")
                ]),
                isVisible: (answers) => Array.isArray(answers.Q2) && answers.Q2.includes("JAVA_FAMILY")
            },
            singleQuestion("Q7_1", "개발할 때 노트북이 뜨거워지거나 팬이 크게 도나요?", [
                option("OFTEN", "자주 그렇다"),
                option("FINE", "가끔 / 별로 없다"),
                option("UNKNOWN", "잘 모르겠다")
            ]),
            singleQuestion("Q8", "이 노트북을 얼마나 쓸 생각인가요?", [
                option("TWO_YEARS", "2년 정도 (곧 바꿀 수 있다)"),
                option("THREE_TO_FOUR", "3~4년"),
                option("FIVE_PLUS_YEARS", "5년 이상 / 오래 쓰고 싶다"),
                option("UNDECIDED", "아직 안 정했다")
            ])
        ]),
        products,
        calculateRecommendation: policy.calculateRecommendation,
        createBaselineRecommendation: policy.createBaselineRecommendation
    });

    function singleQuestion(id, title, options) {
        return {id, title, groups: [{id, options}]};
    }

    function option(id, label, description) {
        return {id, label, description};
    }

    function product(fields) {
        return Object.freeze({
            ...fields,
            imagePath: `/images/products/${fields.id}.webp`,
            imageAlt: `${fields.brand} ${fields.modelName} (${fields.modelCode}) 제품 이미지`,
            priceType: "GENERAL",
            sourceName: "쿠팡",
            checkedAt: "2026-08-14",
            active: true
        });
    }
})();
