import type { ComponentPropsWithoutRef, MouseEvent } from 'react';
import { usePathname } from 'wouter/use-browser-location';
import { track } from './analytics';
import { go } from './routes';
import { useAnalyticsView } from './useAnalyticsView';

type Props = Omit<ComponentPropsWithoutRef<'a'>, 'href' | 'onClick' | 'onAuxClick'> & {
  href: string;
  destination: 'spec_finder' | 'board';
  entryPoint: 'landing_hero' | 'landing_guide' | 'landing_closing' | 'header';
  onNavigate?: () => void;
  measure?: boolean;
};

export function ServiceEntryLink({ href, destination, entryPoint, onNavigate, measure = true, ...props }: Props) {
  const pathname = usePathname();
  const properties = { destination, entry_point: entryPoint };
  const view = useAnalyticsView<HTMLAnchorElement>('service_entry_viewed', properties, pathname, measure);

  function navigate(event: MouseEvent<HTMLAnchorElement>) {
    if (event.button !== 0 && event.button !== 1) return;
    if (measure) {
      // 노출 타이머보다 빠르게 누른 경우도 클릭 전에 노출을 기록한다.
      view.record();
      track('service_entry_clicked', properties);
    }
    if (
      event.button !== 0 ||
      event.metaKey ||
      event.ctrlKey ||
      event.shiftKey ||
      event.altKey ||
      (event.currentTarget.target !== '' && event.currentTarget.target !== '_self')
    ) {
      return;
    }
    event.preventDefault();
    if (onNavigate) onNavigate();
    else go(href);
  }

  return <a {...props} href={href} ref={view.ref} onClick={navigate} onAuxClick={navigate} />;
}
