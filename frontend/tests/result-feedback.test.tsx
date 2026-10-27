import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, test } from 'vitest';
import { ResultView } from '../src/ResultView';

afterEach(cleanup);

test('의견 남기기는 지금 결과 화면 주소를 채운 이슈 양식을 새 탭으로 연다', () => {
  // 답이 여럿이고 같은 질문이 반복돼도 주소가 그대로 전달되는지 본다.
  const resultUrl =
    '/result?category=LAPTOP&answer=PROGRAMMING_LANGUAGE.JAVA_FAMILY&answer=PROGRAMMING_LANGUAGE.OTHERS';
  window.history.pushState({}, '', resultUrl);

  render(
    <ResultView
      specs={[]}
      os="BOTH"
      edits={{}}
      tiers={null}
      tiersFailed={false}
      onRetryTiers={() => {}}
      onChangeOs={() => {}}
      onEdit={() => {}}
      onResetEdits={() => {}}
      onSearch={() => {}}
    />,
  );

  const link = screen.getByRole('link', { name: '의견 남기기 ↗' });
  const url = new URL(link.getAttribute('href') ?? '');
  expect(url.origin + url.pathname).toBe('https://github.com/woowacourse-teams/2026-Devica/issues/new');
  expect(url.searchParams.get('template')).toBe('product-recommendation-feedback.yml');
  expect(url.searchParams.get('title')).toBe('[제품 추천 의견] 결과 화면에서 남긴 의견');
  expect(url.searchParams.get('result_url')).toBe(`https://devica.test${resultUrl}`);
  expect(link.getAttribute('target')).toBe('_blank');
});
