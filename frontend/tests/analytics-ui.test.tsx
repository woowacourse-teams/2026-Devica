import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { track } from '../src/analytics';
import { LandingGuide } from '../src/LandingGuide';
import { LANDING_GUIDES } from '../src/landingGuides';
import { go } from '../src/routes';
import { ServiceEntryLink } from '../src/ServiceEntryLink';
import { SiteHeader } from '../src/SiteHeader';
import { useAnalyticsView } from '../src/useAnalyticsView';

// React 컴포넌트·훅·라우팅은 실제 코드를 사용하고, 외부 전송과 브라우저의 교차 관찰 입력만 제어한다.
vi.mock('../src/analytics', () => ({ track: vi.fn() }));

class ControlledIntersectionObserver {
  static instances: ControlledIntersectionObserver[] = [];
  targets = new Set<Element>();

  constructor(
    readonly callback: IntersectionObserverCallback,
    readonly options: IntersectionObserverInit,
  ) {
    ControlledIntersectionObserver.instances.push(this);
  }

  observe(target: Element) {
    this.targets.add(target);
  }

  disconnect() {
    this.targets.clear();
  }
}

function intersect(target: Element, intersectionRatio: number, isIntersecting = intersectionRatio > 0) {
  const observers = ControlledIntersectionObserver.instances.filter((observer) => observer.targets.has(target));
  expect(observers.length).toBeGreaterThan(0);
  act(() => {
    for (const observer of observers) {
      observer.callback(
        [{ target, intersectionRatio, isIntersecting } as IntersectionObserverEntry],
        observer as unknown as IntersectionObserver,
      );
    }
  });
}

function advance(milliseconds: number) {
  act(() => vi.advanceTimersByTime(milliseconds));
}

let visibility: DocumentVisibilityState;

function changeVisibility(value: DocumentVisibilityState) {
  visibility = value;
  fireEvent(document, new Event('visibilitychange'));
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.mocked(track).mockClear();
  visibility = 'visible';
  vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visibility);
  ControlledIntersectionObserver.instances = [];
  vi.stubGlobal('IntersectionObserver', ControlledIntersectionObserver);
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      disconnect() {}
    },
  );
  window.history.replaceState({}, '', '/');
});

afterEach(() => {
  cleanup();
  vi.clearAllTimers();
  vi.useRealTimers();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

function ViewProbe({ viewKey = '/', enabled = true }: { viewKey?: string; enabled?: boolean }) {
  const view = useAnalyticsView<HTMLHeadingElement>('landing_guide_viewed', { guide: 'find-specs' }, viewKey, enabled);
  return <h2 ref={view.ref}>관찰할 제목</h2>;
}

describe('실제 노출 조건', () => {
  test('50% 미만에서는 기록하지 않고, 50% 이상 연속 300ms 후 한 번 기록한다', () => {
    render(<ViewProbe />);
    const title = screen.getByRole('heading');
    expect(ControlledIntersectionObserver.instances[0].options.threshold).toBe(0.5);
    intersect(title, 0.49);
    advance(1_000);
    expect(track).not.toHaveBeenCalled();
    intersect(title, 0.5);
    advance(299);
    expect(track).not.toHaveBeenCalled();
    advance(1);
    expect(track).toHaveBeenCalledExactlyOnceWith('landing_guide_viewed', { guide: 'find-specs' });
    intersect(title, 0);
    intersect(title, 1);
    advance(300);
    expect(track).toHaveBeenCalledTimes(1);
  });

  test('300ms 전에 벗어나면 취소하고, 돌아온 시점부터 300ms를 다시 센다', () => {
    render(<ViewProbe />);
    const title = screen.getByRole('heading');
    intersect(title, 1);
    advance(200);
    intersect(title, 0);
    advance(500);
    expect(track).not.toHaveBeenCalled();
    intersect(title, 1);
    advance(299);
    expect(track).not.toHaveBeenCalled();
    advance(1);
    expect(track).toHaveBeenCalledTimes(1);
  });

  test('비활성 탭에서는 세지 않고, 대기 중 비활성화돼도 노출 시간을 이어 붙이지 않는다', () => {
    render(<ViewProbe />);
    const title = screen.getByRole('heading');
    changeVisibility('hidden');
    intersect(title, 1);
    advance(1_000);
    expect(track).not.toHaveBeenCalled();
    changeVisibility('visible');
    advance(200);
    changeVisibility('hidden');
    advance(300);
    expect(track).not.toHaveBeenCalled();
    changeVisibility('visible');
    advance(299);
    expect(track).not.toHaveBeenCalled();
    advance(1);
    expect(track).toHaveBeenCalledTimes(1);
  });

  test.each([{ opacity: 0 }, { visibility: 'hidden' as const }, { display: 'none' }])(
    '숨겨진 상위 요소가 있으면 노출로 세지 않는다: %j',
    (style) => {
      render(
        <div style={style}>
          <ViewProbe />
        </div>,
      );
      intersect(screen.getByRole('heading', { hidden: true }), 1);
      advance(300);
      expect(track).not.toHaveBeenCalled();
    },
  );

  test('등장 애니메이션 대기 상태에서는 세지 않고 실제 표시된 뒤부터 센다', async () => {
    const { rerender } = render(
      <div data-landing-reveal="" style={{ opacity: 0 }}>
        <ViewProbe />
      </div>,
    );
    intersect(screen.getByRole('heading'), 1);
    advance(500);
    expect(track).not.toHaveBeenCalled();
    await act(async () => {
      rerender(
        <div data-landing-reveal="visible" style={{ opacity: 1 }}>
          <ViewProbe />
        </div>,
      );
    });
    advance(299);
    expect(track).not.toHaveBeenCalled();
    advance(1);
    expect(track).toHaveBeenCalledTimes(1);
  });

  test('언마운트 시 대기 타이머와 관찰 대상을 해제한다', () => {
    const { unmount } = render(<ViewProbe />);
    intersect(screen.getByRole('heading'), 1);
    advance(200);
    unmount();
    advance(300);
    expect(track).not.toHaveBeenCalled();
    expect(ControlledIntersectionObserver.instances.every((observer) => observer.targets.size === 0)).toBe(true);
  });

  test('경로가 바뀌면 이전 화면의 대기 시간을 취소하고 새 화면의 노출을 관찰한다', () => {
    const { rerender } = render(<ViewProbe />);
    intersect(screen.getByRole('heading'), 1);
    advance(200);
    rerender(<ViewProbe viewKey="/category" />);
    advance(300);
    expect(track).not.toHaveBeenCalled();
    intersect(screen.getByRole('heading'), 1);
    advance(300);
    expect(track).toHaveBeenCalledTimes(1);
    rerender(<ViewProbe viewKey="/board" enabled={false} />);
    advance(300);
    expect(track).toHaveBeenCalledTimes(1);
    rerender(<ViewProbe />);
    intersect(screen.getByRole('heading'), 1);
    advance(300);
    expect(track).toHaveBeenCalledTimes(2);
  });
});

describe.each(LANDING_GUIDES)('$title 카드와 제목 측정', (guide) => {
  test('제목을 빠르게 지나쳐 카드를 넘겨도 제목 조회를 만들지 않는다', () => {
    render(<LandingGuide guide={guide} />);
    const title = screen.getByRole('heading', { name: guide.title, level: 2 });
    const cards = screen.getByRole('region', { name: `${guide.title} 설명 카드. 좌우 방향키로 넘길 수 있습니다.` });
    intersect(title, 1);
    advance(299);
    intersect(title, 0);
    fireEvent.scroll(cards, { target: { scrollLeft: 400 } });
    advance(300);
    expect(track).toHaveBeenCalledExactlyOnceWith('landing_guide_explored', { guide: guide.id });
    expect(screen.getByRole('link', { name: guide.linkText }).getAttribute('href')).toBe(guide.href);
    fireEvent.scroll(cards, { target: { scrollLeft: 800 } });
    expect(track).toHaveBeenCalledTimes(1);

    // 나중에 제목을 실제로 보면 그때 조회를 기록한다. 탐색이 조회를 대신하거나 막지 않는다.
    intersect(title, 0.5);
    advance(300);
    expect(track).toHaveBeenLastCalledWith('landing_guide_viewed', { guide: guide.id });
  });

  test('제목을 본 뒤 카드를 넘기고 시작 버튼을 누르면 실제 순서와 목적지를 기록한다', () => {
    render(<LandingGuide guide={guide} />);
    intersect(screen.getByRole('heading', { name: guide.title, level: 2 }), 1);
    advance(300);
    const cards = screen.getByRole('region', { name: `${guide.title} 설명 카드. 좌우 방향키로 넘길 수 있습니다.` });
    fireEvent.scroll(cards, { target: { scrollLeft: 400 } });
    fireEvent.click(screen.getByRole('link', { name: guide.linkText }));
    const destination = guide.id === 'find-specs' ? 'spec_finder' : 'board';
    expect(vi.mocked(track).mock.calls).toEqual([
      ['landing_guide_viewed', { guide: guide.id }],
      ['landing_guide_explored', { guide: guide.id }],
      ['service_entry_viewed', { destination, entry_point: 'landing_guide' }],
      ['service_entry_clicked', { destination, entry_point: 'landing_guide' }],
    ]);
    expect(window.location.pathname).toBe(guide.href);
  });
});

describe('ServiceEntryLink의 실제 클릭 연결', () => {
  function renderLink(props: { onNavigate?: () => void; target?: string; measure?: boolean } = {}) {
    render(
      <ServiceEntryLink href="/category" destination="spec_finder" entryPoint="landing_hero" {...props}>
        제품 추천 시작
      </ServiceEntryLink>,
    );
    return screen.getByRole('link', { name: '제품 추천 시작' });
  }

  test('관찰 시간 전에 클릭해도 노출·클릭 순서로 기록하고 실제 경로로 이동한다', () => {
    const link = renderLink();
    expect(track).not.toHaveBeenCalled();
    fireEvent.click(link);
    expect(vi.mocked(track).mock.calls).toEqual([
      ['service_entry_viewed', { destination: 'spec_finder', entry_point: 'landing_hero' }],
      ['service_entry_clicked', { destination: 'spec_finder', entry_point: 'landing_hero' }],
    ]);
    expect(window.location.pathname).toBe('/category');
  });

  test('콜백이 있는 링크의 키보드 활성화 클릭도 측정하고 콜백을 한 번 실행한다', () => {
    const onNavigate = vi.fn();
    fireEvent.click(renderLink({ onNavigate }), { detail: 0 });
    expect(track).toHaveBeenLastCalledWith('service_entry_clicked', {
      destination: 'spec_finder',
      entry_point: 'landing_hero',
    });
    expect(onNavigate).toHaveBeenCalledTimes(1);
    expect(window.location.pathname).toBe('/');
  });

  test.each([
    { type: 'click', init: { ctrlKey: true } },
    { type: 'click', init: { metaKey: true } },
    { type: 'click', init: { shiftKey: true } },
    { type: 'click', init: { altKey: true } },
    { type: 'auxclick', init: { button: 1 } },
    { type: 'click', init: {}, target: '_blank' },
  ])('새 탭·수정 키 클릭의 기본 동작을 막지 않으면서 측정한다: %j', ({ type, init, target }) => {
    const onNavigate = vi.fn();
    const link = renderLink({ onNavigate, target });
    const event = new MouseEvent(type, { bubbles: true, cancelable: true, ...init });
    fireEvent(link, event);
    expect(event.defaultPrevented).toBe(false);
    expect(onNavigate).not.toHaveBeenCalled();
    expect(window.location.pathname).toBe('/');
    expect(track).toHaveBeenLastCalledWith('service_entry_clicked', {
      destination: 'spec_finder',
      entry_point: 'landing_hero',
    });
    expect(track).toHaveBeenCalledTimes(2);
  });

  test('오른쪽 마우스 버튼은 진입 클릭으로 기록하지 않는다', () => {
    fireEvent(renderLink(), new MouseEvent('auxclick', { bubbles: true, button: 2 }));
    expect(track).not.toHaveBeenCalled();
  });

  test('measure=false인 링크는 노출·클릭을 수집하지 않고 이동만 처리한다', () => {
    const onNavigate = vi.fn();
    const link = renderLink({ measure: false, onNavigate });
    advance(300);
    fireEvent.click(link);
    expect(track).not.toHaveBeenCalled();
    expect(onNavigate).toHaveBeenCalledTimes(1);
  });

  test('사양 찾기 후 헤더로 게시판에 진입할 수 있고 게시판 안의 재클릭은 새 진입으로 세지 않는다', () => {
    const spec = renderLink();
    const headerProps = {
      onGuide: () => go('/faq'),
      onProductList: () => go('/products'),
      boardHref: '/board',
      onBoard: () => go('/board'),
      boardActive: false,
      productListActive: false,
      guideActive: false,
    };
    const header = render(<SiteHeader {...headerProps} />);
    fireEvent.click(spec);
    expect(window.location.pathname).toBe('/category');
    fireEvent.click(screen.getByRole('link', { name: '질문 게시판' }));
    expect(track).toHaveBeenLastCalledWith('service_entry_clicked', { destination: 'board', entry_point: 'header' });
    expect(window.location.pathname).toBe('/board');
    vi.mocked(track).mockClear();
    header.rerender(<SiteHeader {...headerProps} boardActive />);
    fireEvent.click(screen.getByRole('link', { name: '질문 게시판' }));
    expect(track).not.toHaveBeenCalled();
  });
});
