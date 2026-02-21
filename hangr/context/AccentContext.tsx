import React, { createContext, useCallback, useContext, useState } from 'react';

import {
  AccentKey,
  AccentPalette,
  AccentPalettes,
  DEFAULT_ACCENT,
} from '@/constants/tokens';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

type AccentContextValue = {
  accentKey: AccentKey;
  accent: AccentPalette;
  setAccent: (key: AccentKey) => void;
};

// ---------------------------------------------------------------------------
// Context
// ---------------------------------------------------------------------------

const AccentContext = createContext<AccentContextValue | null>(null);

// ---------------------------------------------------------------------------
// Provider
/**
 * Provides accent context to descendant components, supplying the current accent key, its palette, and a setter.
 *
 * The provider initializes the current accent to the default and makes the following values available via context:
 * `accentKey` — the active accent key; `accent` — the corresponding accent palette; `setAccent` — function to update the accent key.
 *
 * @returns A JSX element that wraps children with AccentContext.Provider exposing `accentKey`, `accent`, and `setAccent`.
 */

export function AccentProvider({ children }: { children: React.ReactNode }) {
  const [accentKey, setAccentKey] = useState<AccentKey>(DEFAULT_ACCENT);

  const setAccent = useCallback((key: AccentKey) => {
    setAccentKey(key);
  }, []);

  return (
    <AccentContext.Provider
      value={{ accentKey, accent: AccentPalettes[accentKey], setAccent }}
    >
      {children}
    </AccentContext.Provider>
  );
}

// ---------------------------------------------------------------------------
// Hook
/**
 * Accesses the current accent context value from the nearest AccentProvider.
 *
 * @returns The current AccentContextValue containing `accentKey`, `accent`, and `setAccent`.
 * @throws {Error} If called outside an AccentProvider (`useAccent must be used within an AccentProvider`).
 */

export function useAccent(): AccentContextValue {
  const ctx = useContext(AccentContext);
  if (!ctx) {
    throw new Error('useAccent must be used within an AccentProvider');
  }
  return ctx;
}