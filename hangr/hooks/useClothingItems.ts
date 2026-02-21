import { useCallback, useEffect, useState } from 'react';

import { getDatabase } from '@/db';
import { getAllClothingItems } from '@/db/queries';
import { ClothingItemWithMeta } from '@/db/types';

type State = {
  items: ClothingItemWithMeta[];
  loading: boolean;
  error: string | null;
};

/**
 * React hook that loads clothing items from the app database and exposes UI-friendly state.
 *
 * @returns An object with `items` (array of `ClothingItemWithMeta`), `loading` (boolean), `error` (string | null), and `refresh` (function to re-run the load operation)
 */
export function useClothingItems() {
  const [state, setState] = useState<State>({ items: [], loading: true, error: null });

  const load = useCallback(async () => {
    setState((s) => ({ ...s, loading: true, error: null }));
    try {
      const db = await getDatabase();
      const items = await getAllClothingItems(db);
      setState({ items, loading: false, error: null });
    } catch (e) {
      setState({ items: [], loading: false, error: String(e) });
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  return { ...state, refresh: load };
}