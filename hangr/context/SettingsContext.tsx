import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from 'react';

import { AccentKey, DEFAULT_ACCENT } from '@/constants/tokens';
import { getDatabase } from '@/db';
import { getAllSettings, setSetting } from '@/db/queries';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export type WeekStartDay = 0 | 1; // 0 = Sunday, 1 = Monday
export type TemperatureUnit = 'F' | 'C';

export type AppSettings = {
  accentKey: AccentKey;
  currencySymbol: string;
  weekStartDay: WeekStartDay;
  temperatureUnit: TemperatureUnit;
  /** When false (default), Sold/Donated/Lost items are hidden from the Closet grid. */
  showArchivedItems: boolean;
};

const DEFAULTS: AppSettings = {
  accentKey: DEFAULT_ACCENT,
  currencySymbol: '$',
  weekStartDay: 0,
  temperatureUnit: 'F',
  showArchivedItems: false,
};

type SettingsContextValue = {
  settings: AppSettings;
  setSetting: <K extends keyof AppSettings>(key: K, value: AppSettings[K]) => Promise<void>;
  loaded: boolean;
};

// ---------------------------------------------------------------------------
// Context
// ---------------------------------------------------------------------------

const SettingsContext = createContext<SettingsContextValue | null>(null);

// ---------------------------------------------------------------------------
// Helpers: row ↔ typed
// ---------------------------------------------------------------------------

function rowsToSettings(rows: Record<string, string>): AppSettings {
  return {
    accentKey: (rows['accent_key'] as AccentKey) ?? DEFAULTS.accentKey,
    currencySymbol: rows['currency_symbol'] ?? DEFAULTS.currencySymbol,
    weekStartDay: (Number(rows['week_start_day'] ?? DEFAULTS.weekStartDay) as WeekStartDay),
    temperatureUnit: (rows['temperature_unit'] as TemperatureUnit) ?? DEFAULTS.temperatureUnit,
    showArchivedItems: rows['show_archived_items'] === '1',
  };
}

function settingToRow<K extends keyof AppSettings>(key: K, value: AppSettings[K]): string {
  if (typeof value === 'boolean') return value ? '1' : '0';
  return String(value);
}

const DB_KEY: Record<keyof AppSettings, string> = {
  accentKey: 'accent_key',
  currencySymbol: 'currency_symbol',
  weekStartDay: 'week_start_day',
  temperatureUnit: 'temperature_unit',
  showArchivedItems: 'show_archived_items',
};

// ---------------------------------------------------------------------------
// Provider
// ---------------------------------------------------------------------------

export function SettingsProvider({ children }: { children: React.ReactNode }) {
  const [settings, setSettings] = useState<AppSettings>(DEFAULTS);
  const [loaded, setLoaded] = useState(false);

  // Load on mount
  useEffect(() => {
    (async () => {
      try {
        const db = await getDatabase();
        const rows = await getAllSettings(db);
        setSettings(rowsToSettings(rows));
      } catch {
        // Use defaults on error — app still works
      } finally {
        setLoaded(true);
      }
    })();
  }, []);

  const update = useCallback(async <K extends keyof AppSettings>(
    key: K,
    value: AppSettings[K]
  ) => {
    // Optimistic local update
    setSettings((prev) => ({ ...prev, [key]: value }));
    try {
      const db = await getDatabase();
      await setSetting(db, DB_KEY[key], settingToRow(key, value));
    } catch {
      // Swallow — in-memory value is still updated for the session
    }
  }, []);

  return (
    <SettingsContext.Provider value={{ settings, setSetting: update, loaded }}>
      {children}
    </SettingsContext.Provider>
  );
}

// ---------------------------------------------------------------------------
// Hook
// ---------------------------------------------------------------------------

export function useSettings(): SettingsContextValue {
  const ctx = useContext(SettingsContext);
  if (!ctx) throw new Error('useSettings must be used within a SettingsProvider');
  return ctx;
}
