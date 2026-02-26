import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
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

const VALID_ACCENT_KEYS: AccentKey[] = ['amber', 'coral', 'sage', 'sky', 'lavender', 'rose'];
const VALID_TEMP_UNITS: TemperatureUnit[] = ['F', 'C'];

function rowsToSettings(rows: Record<string, string>): AppSettings {
  const rawAccent = rows['accent_key'];
  const accentKey: AccentKey = VALID_ACCENT_KEYS.includes(rawAccent as AccentKey)
    ? (rawAccent as AccentKey)
    : DEFAULTS.accentKey;

  const rawCurrency = rows['currency_symbol'];
  const currencySymbol = rawCurrency && rawCurrency.length > 0
    ? rawCurrency
    : DEFAULTS.currencySymbol;

  const rawWeek = Number(rows['week_start_day']);
  const weekStartDay: WeekStartDay = (rawWeek === 0 || rawWeek === 1)
    ? rawWeek
    : DEFAULTS.weekStartDay;

  const rawTemp = rows['temperature_unit'];
  const temperatureUnit: TemperatureUnit = VALID_TEMP_UNITS.includes(rawTemp as TemperatureUnit)
    ? (rawTemp as TemperatureUnit)
    : DEFAULTS.temperatureUnit;

  return {
    accentKey,
    currencySymbol,
    weekStartDay,
    temperatureUnit,
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
  const previousSettingsRef = useRef<AppSettings>(DEFAULTS);

  // Load on mount
  useEffect(() => {
    (async () => {
      try {
        const db = await getDatabase();
        const rows = await getAllSettings(db);
        setSettings(rowsToSettings(rows));
      } catch (e) {
        if (__DEV__) console.error('[SettingsContext] load failed', e);
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
    // Capture previous locally for rollback, then optimistically update
    let prev: AppSettings = previousSettingsRef.current;
    setSettings((current) => {
      prev = current;
      return { ...current, [key]: value };
    });
    try {
      const db = await getDatabase();
      await setSetting(db, DB_KEY[key], settingToRow(key, value));
    } catch (e) {
      if (__DEV__) console.error('[SettingsContext] save failed', e);
      // Rollback to the state captured before this specific call
      setSettings(prev);
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
