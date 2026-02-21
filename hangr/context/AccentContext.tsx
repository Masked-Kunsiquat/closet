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
// ---------------------------------------------------------------------------

type AccentProviderProps = {
  children: React.ReactNode;
  /** Starting accent key — defaults to DEFAULT_ACCENT if not provided. */
  initialKey?: AccentKey;
  /** Called whenever the user changes the accent. Use to persist the value. */
  onAccentChange?: (key: AccentKey) => void;
};

export function AccentProvider({ children, initialKey, onAccentChange }: AccentProviderProps) {
  const [accentKey, setAccentKey] = useState<AccentKey>(initialKey ?? DEFAULT_ACCENT);

  const setAccent = useCallback((key: AccentKey) => {
    setAccentKey(key);
    onAccentChange?.(key);
  }, [onAccentChange]);

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
// ---------------------------------------------------------------------------

export function useAccent(): AccentContextValue {
  const ctx = useContext(AccentContext);
  if (!ctx) {
    throw new Error('useAccent must be used within an AccentProvider');
  }
  return ctx;
}
