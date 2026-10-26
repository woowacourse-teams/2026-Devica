const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

export type QuestionOption = {
  code: string;
  content: string;
  description: string | null;
};

export type Question = {
  code: string;
  title: string;
  description: string | null;
  // 여러 질문을 한 화면에 묶을 때 질문 제목 대신 쓰는 짧은 이름이다.
  label: string | null;
  input: 'SINGLE' | 'MULTI';
  // 고르면 같은 질문의 나머지 답이 지워지는 선택지다. "아직 정하지 않았다" 같은 것.
  exclusive: string | null;
  showWhen: { question: string; anyOf: string[] } | null;
  options: QuestionOption[];
};

// 서버가 질문을 화면 단위(단계)로 묶어 내려준다. 제목이 없는 단계는 질문 하나가 그대로 한 화면이다.
type Step = {
  title: string | null;
  layout: 'CURRENT_SPEC' | null;
  questions: Question[];
};

export type Survey = {
  steps: Step[];
};

export async function fetchSurvey(purposeCode: string): Promise<Survey> {
  const response = await fetch(`${BASE_URL}/api/usage-purposes/${purposeCode}/survey`, {
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new Error(`질문을 불러오지 못했습니다 (${response.status})`);
  }
  return response.json();
}

// 답은 질문 코드마다 여러 개일 수 있다. 서버도 같은 이름을 반복해 받는다.
export type Answers = Record<string, string[]>;

export type Screen = {
  title: string;
  description: string | null;
  currentSpec: boolean;
  questions: { question: Question; label: string | null }[];
};

function isVisible(question: Question, answers: Answers): boolean {
  const showWhen = question.showWhen;
  if (showWhen === null) {
    return true;
  }
  return (answers[showWhen.question] ?? []).some((option) => showWhen.anyOf.includes(option));
}

/**
 * 답에 따라 안 보이는 질문이 생기므로 화면 목록도 답이 바뀔 때마다 다시 계산한다.
 * 화면에 보이는 질문이 하나도 없으면 그 화면은 건너뛴다.
 */
export function resolveScreens(survey: Survey, answers: Answers): Screen[] {
  return survey.steps.flatMap((step) => {
    const visible = step.questions.filter((question) => isVisible(question, answers));

    if (visible.length === 0) {
      return [];
    }

    const single = step.title === null && visible.length === 1;
    return [
      {
        title: step.title ?? visible[0].title,
        description: single ? visible[0].description : null,
        currentSpec: step.layout === 'CURRENT_SPEC',
        questions: visible.map((question) => ({
          question,
          label: question.label ?? (single ? null : question.title),
        })),
      },
    ];
  });
}

// 숨겨진 질문의 답은 지운다. 화면에서 사라져도 값이 남아 있으면 서버가 계산에 쓴다.
// 설문 순서대로 보며 앞에서 지운 답으로 뒤 질문의 노출을 판단해, 서버와 같은 결과를 낸다.
export function pruneHiddenAnswers(survey: Survey, answers: Answers): Answers {
  const pruned: Answers = {};
  for (const question of survey.steps.flatMap((step) => step.questions)) {
    const chosen = answers[question.code];
    if (chosen !== undefined && chosen.length > 0 && isVisible(question, pruned)) {
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
