import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import {
  type CpuTier,
  EMPTY_CONDITION,
  editedSpec,
  editProperties,
  productListQuery,
  specCondition,
} from '../src/products';
import { ResultView } from '../src/ResultView';
import type { Spec } from '../src/recommendation';
import { editEntries, editsOf } from '../src/routes';

afterEach(cleanup);

const TIERS: CpuTier[] = [
  { code: 'BASIC', name: 'M 칩', os: 'MAC' },
  { code: 'PRO', name: 'M Pro 칩', os: 'MAC' },
  { code: 'U', name: '저전력 Core Ultra 5 / Ryzen 5', os: 'WINDOWS' },
];

const item = (code: string, value: string, displayValue: string) => ({
  code,
  displayName: code,
  value,
  displayValue,
  reasons: [`${code} 근거`],
});

const MAC: Spec = {
  preferred: true,
  items: [
    item('OS', 'MAC', 'Mac'),
    item('REQUIRED_CPU', 'PRO', 'M Pro 칩'),
    item('MEMORY', '24', '24GB'),
    item('STORAGE', '1024', '1TB'),
  ],
};

test('고친 사양은 주소에 남았다가 그대로 읽힌다', () => {
  const edits = { MAC: { MEMORY: '16', REQUIRED_CPU: 'BASIC' } };
  expect(editsOf(new URLSearchParams(editEntries(edits)))).toEqual(edits);
  // 형식이 맞지 않는 값은 버린다.
  expect(editsOf(new URLSearchParams('edit=MAC.MEMORY&edit=MAC.MEMORY.16.1'))).toEqual({});
});

test('맞춤 목록의 검색 조건은 고친 사양으로 채워져, 권장보다 낮게도 찾는다', () => {
  const edited = editedSpec(MAC, { MAC: { MEMORY: '16', REQUIRED_CPU: 'BASIC', STORAGE: '512' } }, TIERS);
  const query = productListQuery('RECOMMENDED', { ...EMPTY_CONDITION, ...specCondition(edited) });
  expect(query.get('os')).toBe('MAC');
  expect(query.get('memoryGb')).toBe('16');
  expect(query.get('cpuTier')).toBe('BASIC');
  expect(query.get('storageGb')).toBe('512');
});

test('고를 수 없는 값과 다른 OS 의 등급은 버린다', () => {
  const edited = editedSpec(MAC, { MAC: { MEMORY: '7', REQUIRED_CPU: 'U' } }, TIERS);
  expect(edited.items).toEqual(MAC.items);
  expect(editProperties(MAC, edited)).toBeNull();
});

test('이벤트 속성은 항목마다 권장 값과 고친 값을 담고, 고치지 않은 항목의 고친 값은 비운다', () => {
  const edited = editedSpec(MAC, { MAC: { MEMORY: '32' } }, TIERS);
  expect(editProperties(MAC, edited)).toEqual({
    os: 'MAC',
    cpu_tier_recommended: 'PRO',
    cpu_tier_edited: null,
    memory_gb_recommended: '24',
    memory_gb_edited: '32',
    storage_gb_recommended: '1024',
    storage_gb_edited: null,
  });
});

test('추천 결과 직접 수정을 누르면 항목을 고를 수 있고, 권장 값으로 고르면 되돌린다', () => {
  const onEdit = vi.fn();
  render(
    <ResultView
      specs={[MAC]}
      os="MAC"
      edits={{ MAC: { MEMORY: '32' } }}
      tiers={TIERS}
      tiersFailed={false}
      onRetryTiers={() => {}}
      onChangeOs={() => {}}
      onEdit={onEdit}
      onResetEdits={() => {}}
      onSearch={() => {}}
    />,
  );
  expect(screen.getByText('직접 수정 · 추천 24GB')).toBeTruthy();

  fireEvent.click(screen.getByRole('button', { name: '추천 결과 직접 수정' }));
  const memory = within(screen.getByRole('group', { name: 'MEMORY' }));
  expect(memory.getByRole('button', { name: '32GB' }).getAttribute('aria-pressed')).toBe('true');
  fireEvent.click(memory.getByRole('button', { name: '24GB · 추천' }));
  expect(onEdit).toHaveBeenLastCalledWith('MAC', 'MEMORY', null);
  fireEvent.click(within(screen.getByRole('group', { name: 'PROCESSOR' })).getByRole('button', { name: 'M 칩' }));
  expect(onEdit).toHaveBeenLastCalledWith('MAC', 'REQUIRED_CPU', 'BASIC');
});

test('필터는 고른 값 그대로 걸린다. 맞춤 목록도 전체 목록과 같은 규칙이다', () => {
  const query = productListQuery('PRICE_ASC', { ...EMPTY_CONDITION, os: 'MAC', memoryGb: 16, cpuTier: 'BASIC' });
  expect(query.toString()).toBe('os=MAC&cpuTier=BASIC&memoryGb=16&sort=PRICE_ASC');
});

test('등급을 받지 못했어도 고친 CPU 는 버리지 않는다', () => {
  const edited = editedSpec(MAC, { MAC: { REQUIRED_CPU: 'BASIC' } }, null);
  expect(specCondition(edited).cpuTier).toBe('BASIC');
  expect(editProperties(MAC, edited)?.cpu_tier_edited).toBe('BASIC');
});

test('등급을 받지 못하면 CPU 칸에 실패를 알리고 다시 시도할 수 있다', () => {
  const onRetryTiers = vi.fn();
  render(
    <ResultView
      specs={[MAC]}
      os="MAC"
      edits={{ MAC: { REQUIRED_CPU: 'BASIC' } }}
      tiers={null}
      tiersFailed
      onRetryTiers={onRetryTiers}
      onChangeOs={() => {}}
      onEdit={() => {}}
      onResetEdits={() => {}}
      onSearch={() => {}}
    />,
  );
  expect(screen.getByText('직접 수정 · 추천 M Pro 칩')).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));
  expect(onRetryTiers).toHaveBeenCalled();
});

test('되돌리기는 고친 OS 카드에만 보이고, 그 OS 의 고친 값만 지운다', () => {
  const WINDOWS: Spec = {
    preferred: false,
    items: [
      item('OS', 'WINDOWS', 'Windows'),
      item('REQUIRED_CPU', 'U', '저전력 Core Ultra 5 / Ryzen 5'),
      item('MEMORY', '24', '24GB'),
      item('STORAGE', '512', '512GB'),
    ],
  };
  const onResetEdits = vi.fn();
  const view = (os: 'MAC' | 'WINDOWS') => (
    <ResultView
      specs={[MAC, WINDOWS]}
      os={os}
      edits={{ MAC: { MEMORY: '32' } }}
      tiers={TIERS}
      tiersFailed={false}
      onRetryTiers={() => {}}
      onChangeOs={() => {}}
      onEdit={() => {}}
      onResetEdits={onResetEdits}
      onSearch={() => {}}
    />
  );
  const { rerender } = render(view('WINDOWS'));
  expect(screen.queryByRole('button', { name: '추천 결과로 되돌리기' })).toBeNull();

  rerender(view('MAC'));
  fireEvent.click(screen.getByRole('button', { name: '추천 결과로 되돌리기' }));
  expect(onResetEdits).toHaveBeenCalledWith('MAC');
});
