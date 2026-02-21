import { useCallback, useEffect, useRef, useState } from 'react';

import { getDatabase } from '@/db';
import {
  getBreakdownByBrand,
  getBreakdownByCategory,
  getBreakdownByColor,
  getBreakdownByMaterial,
  getBreakdownByOccasion,
  getBreakdownBySeason,
  getLeastWornItems,
  getMostWornItems,
  getNeverWornItems,
  getStatsOverview,
} from '@/db/queries';
import {
  BreakdownRow,
  ColorBreakdownRow,
  StatItem,
  StatsOverview,
} from '@/db/types';

export type StatsData = {
  overview: StatsOverview;
  mostWorn: StatItem[];
  leastWorn: StatItem[];
  neverWorn: StatItem[];
  byCategory: BreakdownRow[];
  byColor: ColorBreakdownRow[];
  byBrand: BreakdownRow[];
  byMaterial: BreakdownRow[];
  byOccasion: BreakdownRow[];
  bySeason: BreakdownRow[];
};

type State = {
  data: StatsData | null;
  loading: boolean;
  error: string | null;
};

/**
 * Fetches all statistics needed for the Stats dashboard and exposes load state and a refresh function.
 *
 * Wear-based queries (overview, mostWorn, leastWorn, neverWorn) are filtered by `fromDate`;
 * inventory breakdown queries (byCategory, byColor, byBrand, byMaterial, byOccasion, bySeason)
 * always reflect the full active closet regardless of date.
 *
 * @param fromDate - Earliest date (`YYYY-MM-DD`) to include in wear counts, or `null` for all time
 * @returns An object with `data`, `loading`, `error`, and `refresh`
 */
export function useStats(fromDate: string | null) {
  const [state, setState] = useState<State>({ data: null, loading: true, error: null });
  const reqRef = useRef(0);

  const load = useCallback(async () => {
    const reqId = ++reqRef.current;
    setState((s) => ({ ...s, loading: true, error: null }));
    try {
      const db = await getDatabase();
      const [
        overview,
        mostWorn,
        leastWorn,
        neverWorn,
        byCategory,
        byColor,
        byBrand,
        byMaterial,
        byOccasion,
        bySeason,
      ] = await Promise.all([
        getStatsOverview(db, fromDate),
        getMostWornItems(db, fromDate),
        getLeastWornItems(db, fromDate),
        getNeverWornItems(db, fromDate),
        getBreakdownByCategory(db),
        getBreakdownByColor(db),
        getBreakdownByBrand(db),
        getBreakdownByMaterial(db),
        getBreakdownByOccasion(db),
        getBreakdownBySeason(db),
      ]);

      if (reqRef.current !== reqId) return;

      setState({
        data: {
          overview,
          mostWorn,
          leastWorn,
          neverWorn,
          byCategory,
          byColor,
          byBrand,
          byMaterial,
          byOccasion,
          bySeason,
        },
        loading: false,
        error: null,
      });
    } catch (e) {
      if (reqRef.current !== reqId) return;
      setState({ data: null, loading: false, error: String(e) });
    }
  }, [fromDate]);

  useEffect(() => {
    load();
    return () => { reqRef.current++; }; // invalidate in-flight request on unmount/fromDate change
  }, [load]);

  return { ...state, refresh: load };
}
