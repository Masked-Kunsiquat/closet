import { useEffect, useState } from 'react';
import { useColorScheme as useRNColorScheme } from 'react-native';

/**
 * Provide the current color scheme, falling back to 'light' during server-side or pre-hydration renders.
 *
 * Before client hydration this hook returns `'light'`; after hydration it returns the system color scheme reported by React Native's `useColorScheme` (`'light'`, `'dark'`, or `null`).
 *
 * @returns `'light' | 'dark' | null` — `'light'` before hydration, otherwise the system color scheme
 */
export function useColorScheme() {
  const [hasHydrated, setHasHydrated] = useState(false);

  useEffect(() => {
    setHasHydrated(true);
  }, []);

  const colorScheme = useRNColorScheme();

  if (hasHydrated) {
    return colorScheme;
  }

  return 'light';
}