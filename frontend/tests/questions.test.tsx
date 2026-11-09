import { expect, test } from 'vitest';
import { pruneHiddenAnswers, type Survey } from '../src/questions';

test('지금 선택지에 없는 답은 지운다', () => {
  const survey: Survey = {
    version: null,
    steps: [
      {
        title: 'AI 도구',
        layout: null,
        questions: [
          {
            code: 'AI_CODING_TOOL',
            title: '어떤 AI 도구를 쓰나요?',
            description: null,
            label: null,
            input: 'MULTI',
            exclusive: null,
            showWhen: null,
            options: [
              { code: 'AI_EDITOR', content: '에디터 안에서 쓰는 AI', description: null },
              { code: 'AI_CHAT', content: '따로 쓰는 AI 채팅', description: null },
            ],
          },
        ],
      },
    ],
  };

  expect(pruneHiddenAnswers(survey, { AI_CODING_TOOL: ['IDE_EXTENSION', 'AI_CHAT'] })).toEqual({
    AI_CODING_TOOL: ['AI_CHAT'],
  });
  expect(pruneHiddenAnswers(survey, { AI_CODING_TOOL: ['IDE_EXTENSION'] })).toEqual({});
});
