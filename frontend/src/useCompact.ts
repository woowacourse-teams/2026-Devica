import { useEffect, useState } from 'react';
import { DESKTOP_MEDIA } from './routes';

export function useCompact(): boolean {
  const [compact, setCompact] = useState(() => window.matchMedia?.(DESKTOP_MEDIA).matches === false);
  useEffect(() => {
    const query = window.matchMedia?.(DESKTOP_MEDIA);
    if (query === undefined) {
      return;
    }
    const sync = () => setCompact(!query.matches);
    query.addEventListener('change', sync);
    return () => query.removeEventListener('change', sync);
  }, []);
  return compact;
}
