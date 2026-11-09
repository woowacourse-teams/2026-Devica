import { describe, expect, it } from 'vitest';
import { screenOf } from '../src/routes';

describe('screenOf', () => {
  it('주소 끝의 / 와 관계없이 같은 화면으로 본다', () => {
    expect(screenOf('/faq/')).toBe('faq');
    expect(screenOf('/board/')).toBe('board');
    expect(screenOf('/board/posts/3/')).toBe('boardPost');
    expect(screenOf('/')).toBe('home');
  });
});
