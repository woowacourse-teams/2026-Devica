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
      // 빠른 클릭이나 새 세션의 클릭도 노출을 보장한다. 같은 세션의 중복은 전송 전에 걸러진다.
      track('service_entry_viewed', properties);
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
