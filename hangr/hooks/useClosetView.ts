import { useMemo, useState } from 'react';

import { ClothingItemWithMeta } from '@/db/types';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export type ViewMode = 'grid' | 'list';

export type SortKey =
  | 'recently_added'
  | 'name_asc'
  | 'name_desc'
  | 'most_worn'
  | 'least_worn'
  | 'purchase_date';

export type ActiveFilters = {
  categoryId: number | null;
  subcategoryId: number | null;
  /** When set, only items with this color are shown. Resolved via junction table. */
  colorId: number | null;
  /** When set, only items with this season are shown. Resolved via junction table. */
  seasonId: number | null;
  /** When set, only items with this occasion are shown. Resolved via junction table. */
  occasionId: number | null;
  brand: string | null;
  status: 'Active' | 'Sold' | 'Donated' | 'Lost' | null;
  /**
   * Item IDs that match the current colorId/seasonId/occasionId junction filters.
   * Populated by FilterPanel after running the junction queries. null = not filtered.
   */
  junctionItemIds: Set<number> | null;
};

export const EMPTY_FILTERS: ActiveFilters = {
  categoryId: null,
  subcategoryId: null,
  colorId: null,
  seasonId: null,
  occasionId: null,
  brand: null,
  status: null,
  junctionItemIds: null,
};

export const SORT_LABELS: Record<SortKey, string> = {
  recently_added: 'Recently Added',
  name_asc:       'Name (A–Z)',
  name_desc:      'Name (Z–A)',
  most_worn:      'Most Worn',
  least_worn:     'Least Worn',
  purchase_date:  'Purchase Date',
};

// ---------------------------------------------------------------------------
// Hook

const ARCHIVED_STATUSES = new Set(['Sold', 'Donated', 'Lost']);

/**
 * Manages view mode, sorting, and active filters for a closet item list.
 *
 * Provides local state and derived results for rendering and controlling a closet view:
 * - current view mode and setter (`viewMode`, `setViewMode`)
 * - current sort key and setter (`sortKey`, `setSortKey`)
 * - active filter state and updaters (`filters`, `setFilter`, `applyFilters`, `clearFilters`)
 * - filter panel visibility and setter (`filterPanelOpen`, `setFilterPanelOpen`)
 * - `activeFilterCount`: number of non-null scalar filters currently applied
 * - `filteredAndSorted`: the input items filtered and ordered according to `filters` and `sortKey`
 *
 * @param items - The list of clothing items with metadata to be filtered and sorted
 * @returns An object containing view and filter state, mutators, `activeFilterCount`, and `filteredAndSorted`
 */

export function useClosetView(items: ClothingItemWithMeta[], showArchivedItems = true) {
  const [viewMode, setViewMode] = useState<ViewMode>('grid');
  const [sortKey, setSortKey] = useState<SortKey>('recently_added');
  const [filters, setFilters] = useState<ActiveFilters>(EMPTY_FILTERS);
  const [filterPanelOpen, setFilterPanelOpen] = useState(false);

  const activeFilterCount = useMemo(() => {
    const { junctionItemIds, ...rest } = filters;
    return Object.values(rest).filter((v) => v !== null).length;
  }, [filters]);

  const filteredAndSorted = useMemo(() => {
    let result = [...items];

    // Archive exclusion — only when no explicit status filter is active
    if (!showArchivedItems && filters.status === null) {
      result = result.filter((i) => !ARCHIVED_STATUSES.has(i.status));
    }

    // Scalar filters — direct column comparisons
    if (filters.categoryId !== null) {
      result = result.filter((i) => i.category_id === filters.categoryId);
    }
    if (filters.subcategoryId !== null) {
      result = result.filter((i) => i.subcategory_id === filters.subcategoryId);
    }
    if (filters.status !== null) {
      result = result.filter((i) => i.status === filters.status);
    }
    if (filters.brand !== null) {
      const brandLower = filters.brand.toLowerCase();
      result = result.filter((i) => i.brand?.toLowerCase() === brandLower);
    }

    // Junction filters — resolved to a Set<number> of item IDs by FilterPanel
    if (filters.junctionItemIds !== null) {
      result = result.filter((i) => filters.junctionItemIds!.has(i.id));
    }

    // Sort
    result.sort((a, b) => {
      switch (sortKey) {
        case 'recently_added':
          return b.created_at.localeCompare(a.created_at);
        case 'name_asc':
          return a.name.localeCompare(b.name);
        case 'name_desc':
          return b.name.localeCompare(a.name);
        case 'most_worn':
          return b.wear_count - a.wear_count;
        case 'least_worn':
          return a.wear_count - b.wear_count;
        case 'purchase_date':
          if (!a.purchase_date && !b.purchase_date) return 0;
          if (!a.purchase_date) return 1;
          if (!b.purchase_date) return -1;
          return b.purchase_date.localeCompare(a.purchase_date);
      }
    });

    return result;
  }, [items, filters, sortKey, showArchivedItems]);

  const setFilter = <K extends keyof ActiveFilters>(key: K, value: ActiveFilters[K]) => {
    setFilters((f) => ({ ...f, [key]: value }));
  };

  /** Called by FilterPanel to commit a complete new filter state atomically. */
  const applyFilters = (next: ActiveFilters) => setFilters(next);

  const clearFilters = () => setFilters(EMPTY_FILTERS);

  return {
    viewMode,
    setViewMode,
    sortKey,
    setSortKey,
    filters,
    setFilter,
    applyFilters,
    clearFilters,
    filterPanelOpen,
    setFilterPanelOpen,
    activeFilterCount,
    filteredAndSorted,
  };
}