/**
 * FilterPanel — slides up from bottom as a modal sheet.
 * Handles filter selection (category, subcategory, color, season, occasion, brand, status)
 * and sort order. Resolves junction table item IDs before committing.
 */

import { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Animated,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { FontSize, FontWeight, Palette, Radius, Spacing } from '@/constants/tokens';
import { useAccent } from '@/context/AccentContext';
import { getDatabase } from '@/db';
import {
  getCategories,
  getColors,
  getDistinctBrands,
  getItemIdsByColor,
  getItemIdsByOccasion,
  getItemIdsBySeason,
  getOccasions,
  getSeasons,
  getSubcategories,
} from '@/db/queries';
import {
  ActiveFilters,
  EMPTY_FILTERS,
  SORT_LABELS,
  SortKey,
} from '@/hooks/useClosetView';
import type { Category, Color, Occasion, Season, Subcategory } from '@/db/types';

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

type Props = {
  visible: boolean;
  onClose: () => void;
  currentFilters: ActiveFilters;
  currentSort: SortKey;
  onApply: (filters: ActiveFilters, sort: SortKey) => void;
};

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function FilterPanel({ visible, onClose, currentFilters, currentSort, onApply }: Props) {
  const { accent } = useAccent();
  const insets = useSafeAreaInsets();
  const slideAnim = useRef(new Animated.Value(0)).current;

  // Local draft state — committed only on Apply
  const [draft, setDraft] = useState<ActiveFilters>(currentFilters);
  const [draftSort, setDraftSort] = useState<SortKey>(currentSort);

  // Lookup data
  const [categories, setCategories] = useState<Category[]>([]);
  const [subcategories, setSubcategories] = useState<Subcategory[]>([]);
  const [seasons, setSeasons] = useState<Season[]>([]);
  const [occasions, setOccasions] = useState<Occasion[]>([]);
  const [colors, setColors] = useState<Color[]>([]);
  const [brands, setBrands] = useState<string[]>([]);

  useEffect(() => {
    (async () => {
      const db = await getDatabase();
      const [cats, seas, occs, cols, brnds] = await Promise.all([
        getCategories(db),
        getSeasons(db),
        getOccasions(db),
        getColors(db),
        getDistinctBrands(db),
      ]);
      setCategories(cats);
      setSeasons(seas);
      setOccasions(occs);
      setColors(cols);
      setBrands(brnds);
    })();
  }, []);

  // Reload subcategories when draft category changes
  useEffect(() => {
    if (!draft.categoryId) { setSubcategories([]); return; }
    getDatabase().then((db) =>
      getSubcategories(db, draft.categoryId!).then(setSubcategories)
    );
  }, [draft.categoryId]);

  // Sync draft when panel opens with current values
  useEffect(() => {
    if (visible) {
      setDraft(currentFilters);
      setDraftSort(currentSort);
    }
  }, [visible, currentFilters, currentSort]);

  // Slide animation
  useEffect(() => {
    Animated.timing(slideAnim, {
      toValue: visible ? 1 : 0,
      duration: 280,
      useNativeDriver: true,
    }).start();
  }, [visible, slideAnim]);

  const setDraftFilter = <K extends keyof ActiveFilters>(key: K, value: ActiveFilters[K]) => {
    setDraft((d) => ({ ...d, [key]: value }));
  };

  const handleApply = async () => {
    // Resolve junction filters to item ID sets before committing
    let junctionItemIds: Set<number> | null = null;

    const junctionFiltersActive =
      draft.colorId !== null || draft.seasonId !== null || draft.occasionId !== null;

    if (junctionFiltersActive) {
      try {
        const db = await getDatabase();
        const sets: Set<number>[] = [];

        if (draft.colorId !== null) {
          const ids = await getItemIdsByColor(db, draft.colorId);
          sets.push(new Set(ids));
        }
        if (draft.seasonId !== null) {
          const ids = await getItemIdsBySeason(db, draft.seasonId);
          sets.push(new Set(ids));
        }
        if (draft.occasionId !== null) {
          const ids = await getItemIdsByOccasion(db, draft.occasionId);
          sets.push(new Set(ids));
        }

        // Intersection: item must match ALL active junction filters
        if (sets.length === 1) {
          junctionItemIds = sets[0];
        } else {
          junctionItemIds = new Set(
            [...sets[0]].filter((id) => sets.slice(1).every((s) => s.has(id)))
          );
        }
      } catch (e) {
        console.error('[handleApply]', e);
        Alert.alert('Error', 'Could not apply filters. Please try again.');
        return;
      }
    }

    onApply({ ...draft, junctionItemIds }, draftSort);
    onClose();
  };

  const handleClear = () => {
    setDraft(EMPTY_FILTERS);
    setDraftSort('recently_added');
  };

  const translateY = slideAnim.interpolate({
    inputRange: [0, 1],
    outputRange: [600, 0],
  });

  return (
    <Modal
      visible={visible}
      transparent
      animationType="none"
      onRequestClose={onClose}
    >
      {/* Backdrop */}
      <Pressable style={styles.backdrop} onPress={onClose} />

      {/* Sheet */}
      <Animated.View
        style={[
          styles.sheet,
          { paddingBottom: insets.bottom + Spacing[2], transform: [{ translateY }] },
        ]}
      >
        {/* Handle */}
        <View style={styles.handle} />

        {/* Header */}
        <View style={styles.sheetHeader}>
          <TouchableOpacity onPress={handleClear} hitSlop={10}>
            <Text style={styles.clearText}>Clear</Text>
          </TouchableOpacity>
          <Text style={styles.sheetTitle}>Filter & Sort</Text>
          <TouchableOpacity onPress={handleApply} hitSlop={10}>
            <Text style={[styles.applyText, { color: accent.primary }]}>Apply</Text>
          </TouchableOpacity>
        </View>

        <ScrollView
          style={styles.scroll}
          contentContainerStyle={styles.scrollContent}
          showsVerticalScrollIndicator={false}
        >
          {/* Sort */}
          <PanelSection title="Sort By">
            <ChipRow
              items={Object.entries(SORT_LABELS).map(([k, v]) => ({ id: k, name: v }))}
              selectedId={draftSort}
              onSelect={(id) => setDraftSort(id as SortKey)}
              accent={accent.primary}
            />
          </PanelSection>

          {/* Status */}
          <PanelSection title="Status">
            <ChipRow
              items={[
                { id: 'Active',  name: 'Active'  },
                { id: 'Sold',    name: 'Sold'    },
                { id: 'Donated', name: 'Donated' },
                { id: 'Lost',    name: 'Lost'    },
              ]}
              selectedId={draft.status}
              onSelect={(id) =>
                setDraftFilter('status', draft.status === id ? null : id as ActiveFilters['status'])
              }
              accent={accent.primary}
            />
          </PanelSection>

          {/* Category */}
          <PanelSection title="Category">
            <ChipRow
              items={categories}
              selectedId={draft.categoryId}
              onSelect={(id) => {
                const next = draft.categoryId === id ? null : (id as number);
                setDraft((d) => ({ ...d, categoryId: next, subcategoryId: null }));
              }}
              accent={accent.primary}
            />
          </PanelSection>

          {/* Subcategory */}
          {subcategories.length > 0 && (
            <PanelSection title="Subcategory">
              <ChipRow
                items={subcategories}
                selectedId={draft.subcategoryId}
                onSelect={(id) =>
                  setDraftFilter(
                    'subcategoryId',
                    draft.subcategoryId === id ? null : (id as number)
                  )
                }
                accent={accent.primary}
              />
            </PanelSection>
          )}

          {/* Color */}
          <PanelSection title="Color">
            <ChipRow
              items={colors}
              selectedId={draft.colorId}
              onSelect={(id) =>
                setDraftFilter('colorId', draft.colorId === id ? null : (id as number))
              }
              accent={accent.primary}
              renderPrefix={(item: Color) =>
                item.hex ? (
                  <View style={[styles.colorDot, { backgroundColor: item.hex }]} />
                ) : null
              }
            />
          </PanelSection>

          {/* Season */}
          <PanelSection title="Season">
            <ChipRow
              items={seasons}
              selectedId={draft.seasonId}
              onSelect={(id) =>
                setDraftFilter('seasonId', draft.seasonId === id ? null : (id as number))
              }
              accent={accent.primary}
            />
          </PanelSection>

          {/* Occasion */}
          <PanelSection title="Occasion">
            <ChipRow
              items={occasions}
              selectedId={draft.occasionId}
              onSelect={(id) =>
                setDraftFilter('occasionId', draft.occasionId === id ? null : (id as number))
              }
              accent={accent.primary}
            />
          </PanelSection>

          {/* Brand */}
          {brands.length > 0 && (
            <PanelSection title="Brand">
              <ChipRow
                items={brands.map((b) => ({ id: b, name: b }))}
                selectedId={draft.brand}
                onSelect={(id) =>
                  setDraftFilter('brand', draft.brand === id ? null : (id as string))
                }
                accent={accent.primary}
              />
            </PanelSection>
          )}
        </ScrollView>
      </Animated.View>
    </Modal>
  );
}

// ---------------------------------------------------------------------------
// Sub-components
// ---------------------------------------------------------------------------

function PanelSection({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>{title}</Text>
      {children}
    </View>
  );
}

type ChipItem = { id: string | number; name: string };

function ChipRow<T extends ChipItem>({
  items,
  selectedId,
  onSelect,
  accent,
  renderPrefix,
}: {
  items: T[];
  selectedId: string | number | null;
  onSelect: (id: string | number) => void;
  accent: string;
  renderPrefix?: (item: T) => React.ReactNode;
}) {
  return (
    <View style={styles.chips}>
      {items.map((item) => {
        const selected = item.id === selectedId;
        return (
          <Pressable
            key={String(item.id)}
            style={[styles.chip, selected && { backgroundColor: accent, borderColor: accent }]}
            onPress={() => onSelect(item.id)}
          >
            {renderPrefix?.(item)}
            <Text style={[styles.chipText, selected && styles.chipTextSelected]}>
              {item.name}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------

const styles = StyleSheet.create({
  backdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.55)',
  },
  sheet: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: Palette.surface1,
    borderTopLeftRadius: Radius.xl,
    borderTopRightRadius: Radius.xl,
    maxHeight: '85%',
  },
  handle: {
    width: 36,
    height: 4,
    backgroundColor: Palette.border,
    borderRadius: Radius.full,
    alignSelf: 'center',
    marginTop: Spacing[3],
    marginBottom: Spacing[2],
  },
  sheetHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: Spacing[4],
    paddingVertical: Spacing[3],
    borderBottomWidth: 1,
    borderBottomColor: Palette.border,
  },
  sheetTitle: {
    color: Palette.textPrimary,
    fontSize: FontSize.md,
    fontWeight: FontWeight.semibold,
  },
  clearText: {
    color: Palette.textSecondary,
    fontSize: FontSize.md,
    minWidth: 44,
  },
  applyText: {
    fontSize: FontSize.md,
    fontWeight: FontWeight.semibold,
    minWidth: 44,
    textAlign: 'right',
  },
  scroll: {
    flex: 1,
  },
  scrollContent: {
    padding: Spacing[4],
    gap: Spacing[1],
  },
  section: {
    marginBottom: Spacing[5],
  },
  sectionTitle: {
    color: Palette.textSecondary,
    fontSize: FontSize.sm,
    fontWeight: FontWeight.medium,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: Spacing[2],
  },
  chips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing[2],
  },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing[1],
    paddingHorizontal: Spacing[3],
    paddingVertical: Spacing[2],
    borderRadius: Radius.full,
    borderWidth: 1,
    borderColor: Palette.border,
    backgroundColor: Palette.surface2,
  },
  chipText: {
    color: Palette.textSecondary,
    fontSize: FontSize.sm,
  },
  chipTextSelected: {
    color: '#000',
    fontWeight: FontWeight.semibold,
  },
  colorDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.2)',
  },
});
