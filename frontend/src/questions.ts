const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

export type QuestionOption = {
    code: string;
    content: string;
    description: string | null;
    // 고르면 같은 질문의 나머지 답이 지워지는 선택지다. "아직 정하지 않았다" 같은 것.
    exclusive: boolean;
};

export type Question = {
    code: string;
    title: string;
    description: string | null;
    inputType: 'SINGLE' | 'MULTI';
    dependsOn: { questionCode: string; optionCode: string } | null;
    options: QuestionOption[];
};

export async function fetchQuestions(purposeCode: string): Promise<Question[]> {
    const response = await fetch(`${BASE_URL}/api/usage-purposes/${purposeCode}/questions`, {
        headers: {Accept: 'application/json'},
    });
    if (!response.ok) {
        throw new Error(`질문을 불러오지 못했습니다 (${response.status})`);
    }
    return response.json();
}

// 답은 질문 코드마다 여러 개일 수 있다. 서버도 같은 이름을 반복해 받는다.
export type Answers = Record<string, string[]>;

/**
 * 원본은 여러 질문을 한 화면에 묶어 보여준다. API 에는 그 묶음을 나타내는 필드가 없어
 * 여기에 적어 둔다 (#83). 묶이지 않은 화면은 질문 하나가 그대로 한 화면이다.
 */
type ScreenSpec = {
    codes: string[];
    // 묶은 화면은 API 질문 제목을 쓸 수 없어 원본 문구를 적는다.
    title?: string;
    // 현재 쓰는 노트북 사양은 레이블만 달린 칩 목록으로 보여준다.
    currentSpec?: boolean;
    // 묶인 질문의 레이블. 없으면 API 의 title 을 쓴다.
    labels?: Record<string, string>;
};

const SCREEN_SPECS: ScreenSpec[] = [
    {
        title: '현재 사용하는 노트북 사양을 알려주세요',
        currentSpec: true,
        codes: ['CURRENT_OS', 'CURRENT_MAC_CPU', 'CURRENT_WINDOWS_CPU', 'CURRENT_MEMORY', 'CURRENT_STORAGE'],
        labels: {
            CURRENT_OS: 'OS',
            CURRENT_MAC_CPU: 'PROCESSOR',
            CURRENT_WINDOWS_CPU: 'PROCESSOR',
            CURRENT_MEMORY: 'MEMORY',
            CURRENT_STORAGE: 'STORAGE',
        },
    },
    {codes: ['PREFERRED_OS']},
    {codes: ['PROGRAMMING_LANGUAGE']},
    {title: '개발 도구를 알려주세요', codes: ['IDE', 'AI_CODING_TOOL']},
    {codes: ['DEV_ENVIRONMENT_SETUP']},
    {codes: ['SLOWDOWN']},
    {codes: ['STORAGE_SHORTAGE']},
    {codes: ['BUILD_WAIT']},
    {codes: ['OVERHEATING']},
    {codes: ['USAGE_PERIOD']},
];

export type Screen = {
    title: string;
    description: string | null;
    currentSpec: boolean;
    questions: { question: Question; label: string | null }[];
};

function isVisible(question: Question, answers: Answers): boolean {
    const depends = question.dependsOn;
    if (depends === null) {
        return true;
    }
    return (answers[depends.questionCode] ?? []).includes(depends.optionCode);
}

/**
 * 답에 따라 안 보이는 질문이 생기므로 화면 목록도 답이 바뀔 때마다 다시 계산한다.
 * 화면에 보이는 질문이 하나도 없으면 그 화면은 건너뛴다.
 */
export function resolveScreens(questions: Question[], answers: Answers): Screen[] {
    const byCode = new Map(questions.map((question) => [question.code, question]));

    return SCREEN_SPECS.flatMap((spec) => {
        const visible = spec.codes
            .map((code) => byCode.get(code))
            .filter((question): question is Question => question !== undefined)
            .filter((question) => isVisible(question, answers));

        if (visible.length === 0) {
            return [];
        }

        const single = visible.length === 1 && spec.labels === undefined;
        return [{
            title: spec.title ?? visible[0].title,
            description: single ? visible[0].description : null,
            currentSpec: spec.currentSpec ?? false,
            questions: visible.map((question) => ({
                question,
                label: spec.labels?.[question.code] ?? (single ? null : question.title),
            })),
        }];
    });
}

// 답이 지워진 질문에 딸린 답도 함께 지운다. 화면에서 사라져도 값이 남아 있으면 서버가 계산에 쓴다.
export function pruneHiddenAnswers(questions: Question[], answers: Answers): Answers {
    const pruned: Answers = {};
    for (const question of questions) {
        const chosen = answers[question.code];
        if (chosen !== undefined && chosen.length > 0 && isVisible(question, answers)) {
            pruned[question.code] = chosen;
        }
    }
    return pruned;
}

export function toSearchParams(answers: Answers): string {
    const params = new URLSearchParams();
    for (const [code, values] of Object.entries(answers)) {
        for (const value of values) {
            params.append(code, value);
        }
    }
    return params.toString();
}
