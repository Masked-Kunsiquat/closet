import { useCallback, useEffect, useState } from 'react';

import { getDatabase } from '@/db';
import { getAllOutfits } from '@/db/queries';
import { OutfitWithMeta } from '@/db/types';

type State = {
  outfits: OutfitWithMeta[];
  loading: boolean;
  error: string | null;
};

export function useOutfits() {
  const [state, setState] = useState<State>({ outfits: [], loading: true, error: null });

  const load = useCallback(async () => {
    setState((s) => ({ ...s, loading: true, error: null }));
    try {
      const db = await getDatabase();
      const outfits = await getAllOutfits(db);
      setState({ outfits, loading: false, error: null });
    } catch (e) {
      setState((s) => ({ ...s, loading: false, error: String(e) }));
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  return { ...state, refresh: load };
}
