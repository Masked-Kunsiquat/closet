import { useCallback, useEffect, useState } from 'react';

import { getDatabase } from '@/db';
import {
  getClothingItemById,
  getClothingItemColorIds,
  getClothingItemMaterialIds,
  getClothingItemOccasionIds,
  getClothingItemPatternIds,
  getClothingItemSeasonIds,
} from '@/db/queries';
import { ClothingItemWithMeta } from '@/db/types';

export type ClothingItemDetail = ClothingItemWithMeta & {
  wearCount: number;
  costPerWear: number | null;
  colorIds: number[];
  materialIds: number[];
  seasonIds: number[];
  occasionIds: number[];
  patternIds: number[];
};

type State = {
  item: ClothingItemDetail | null;
  loading: boolean;
  error: string | null;
};

export function useClothingItem(id: number) {
  const [state, setState] = useState<State>({ item: null, loading: true, error: null });

  const load = useCallback(async () => {
    setState((s) => ({ ...s, loading: true, error: null }));
    try {
      const db = await getDatabase();
      const [item, colorIds, materialIds, seasonIds, occasionIds, patternIds] =
        await Promise.all([
          getClothingItemById(db, id),
          getClothingItemColorIds(db, id),
          getClothingItemMaterialIds(db, id),
          getClothingItemSeasonIds(db, id),
          getClothingItemOccasionIds(db, id),
          getClothingItemPatternIds(db, id),
        ]);

      if (!item) {
        setState({ item: null, loading: false, error: 'Item not found' });
        return;
      }

      const wearCount = item.wear_count;
      // Cost per wear: null if no price or 0 wears (never divide by zero).
      const costPerWear =
        item.purchase_price != null && wearCount > 0
          ? item.purchase_price / wearCount
          : null;

      setState({
        item: {
          ...item,
          wearCount,
          costPerWear,
          colorIds,
          materialIds,
          seasonIds,
          occasionIds,
          patternIds,
        },
        loading: false,
        error: null,
      });
    } catch (e) {
      setState({ item: null, loading: false, error: String(e) });
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  return { ...state, refresh: load };
}
