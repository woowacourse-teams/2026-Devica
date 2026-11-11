import { describe, expect, it } from 'vitest';
import { positiveOf, screenOf } from '../src/routes';

describe('screenOf', () => {
  it('주소 끝의 / 와 관계없이 같은 화면으로 본다', () => {
    expect(screenOf('/faq/')).toBe('faq');
    expect(screenOf('/board/')).toBe('board');
    expect(screenOf('/board/posts/3/')).toBe('boardPost');
    expect(screenOf('/')).toBe('home');
  });
});

describe('positiveOf', () => {
  it('정확히 나타낼 수 없는 큰 수는 없는 값으로 본다', () => {
    expect(positiveOf('9007199254740991')).toBe(9007199254740991);
    expect(positiveOf('99999999999999999999')).toBeNull();
  });
});
