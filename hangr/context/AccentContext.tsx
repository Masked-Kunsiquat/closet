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
// ---------------------------------------------------------------------------

export function useAccent(): AccentContextValue {
  const ctx = useContext(AccentContext);
  if (!ctx) {
    throw new Error('useAccent must be used within an AccentProvider');
  }
  return ctx;
}
