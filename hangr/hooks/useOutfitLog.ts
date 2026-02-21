import { useCallback, useEffect, useState } from 'react';

import { getDatabase } from '@/db';
import { getCalendarDaysForMonth, getLogsByDate } from '@/db/queries';
import { CalendarDay, OutfitLogWithMeta } from '@/db/types';

// ---------------------------------------------------------------------------
// Logs for a single date
// ---------------------------------------------------------------------------

type DayState = {
  logs: OutfitLogWithMeta[];
  loading: boolean;
  error: string | null;
};

/**
 * Provides outfit logs and loading/error state for a specific calendar date.
 *
 * @param date - The date identifier for which to load logs (string format expected by the data layer)
 * @returns An object containing:
 *  - `logs`: an array of outfit log entries for the date,
 *  - `loading`: `true` while a fetch is in progress, `false` otherwise,
 *  - `error`: an error message string when a fetch fails, or `null` when there is no error,
 *  - `refresh`: a function that re-fetches the logs for the same date
 */
export function useLogsForDate(date: string) {
  const [state, setState] = useState<DayState>({ logs: [], loading: true, error: null });

  const load = useCallback(async () => {
    setState((s) => ({ ...s, loading: true, error: null }));
    try {
      const db = await getDatabase();
      const logs = await getLogsByDate(db, date);
      setState({ logs, loading: false, error: null });
    } catch (e) {
      setState((s) => ({ ...s, loading: false, error: String(e) }));
    }
  }, [date]);

  useEffect(() => { load(); }, [load]);

  return { ...state, refresh: load };
}

// ---------------------------------------------------------------------------
// Calendar data for a month
// ---------------------------------------------------------------------------

type MonthState = {
  days: CalendarDay[];
  loading: boolean;
  error: string | null;
};

/**
 * Load and expose calendar day entries for a specific month.
 *
 * @param yearMonth - The target month in `YYYY-MM` format used to fetch calendar days
 * @returns An object containing:
 *  - `days`: the array of calendar day entries for the month
 *  - `loading`: `true` while data is being fetched, `false` otherwise
 *  - `error`: a string error message when fetching failed, or `null` if none
 *  - `refresh`: a function to re-fetch the month's calendar days
 */
export function useCalendarMonth(yearMonth: string) {
  const [state, setState] = useState<MonthState>({ days: [], loading: true, error: null });

  const load = useCallback(async () => {
    setState((s) => ({ ...s, loading: true, error: null }));
    try {
      const db = await getDatabase();
      const days = await getCalendarDaysForMonth(db, yearMonth);
      setState({ days, loading: false, error: null });
    } catch (e) {
      setState((s) => ({ ...s, loading: false, error: String(e) }));
    }
  }, [yearMonth]);

  useEffect(() => { load(); }, [load]);

  return { ...state, refresh: load };
}