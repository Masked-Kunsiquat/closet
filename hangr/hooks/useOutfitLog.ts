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
