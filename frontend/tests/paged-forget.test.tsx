import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, expect, test } from 'vitest';
import { forgetStartingWith, type Page, usePaged } from '../src/fetched';

type Item = { id: number };

afterEach(cleanup);

test('받는 중에 버려진 다음 페이지 응답은 남기지 않고, 돌아오면 첫 페이지부터 다시 받는다', async () => {
  const pending: { page: number; resolve: (page: Page<Item>) => void }[] = [];
  const fetchPage = (page: number) => new Promise<Page<Item>>((resolve) => pending.push({ page, resolve }));
  const key = 'forget-test:list';

  const first = renderHook(() => usePaged<Item>(key, fetchPage));
  await act(async () => pending[0].resolve({ content: [{ id: 1 }], hasNext: true }));
  act(() => first.result.current.more());
  expect(pending[1].page).toBe(1);
  first.unmount();

  forgetStartingWith('forget-test:');
  await act(async () => pending[1].resolve({ content: [{ id: 2 }], hasNext: false }));

  const second = renderHook(() => usePaged<Item>(key, fetchPage));
  await waitFor(() => expect(pending).toHaveLength(3));
  expect(pending[2].page).toBe(0);
  await act(async () => pending[2].resolve({ content: [{ id: 9 }, { id: 1 }], hasNext: true }));
  expect(second.result.current.data).toEqual([{ id: 9 }, { id: 1 }]);
});

test('첫 페이지가 실패한 목록에 다시 들어오면 다시 받는다', async () => {
  const rejects: (() => void)[] = [];
  const fetchPage = () => new Promise<Page<Item>>((_, reject) => rejects.push(() => reject(new Error('fail'))));
  const key = 'failed-test:list';

  const first = renderHook(() => usePaged<Item>(key, fetchPage));
  await act(async () => rejects[0]());
  expect(first.result.current.failed).toBe(true);
  expect(rejects).toHaveLength(1);
  first.unmount();

  renderHook(() => usePaged<Item>(key, fetchPage));
  await waitFor(() => expect(rejects).toHaveLength(2));
});
