/**
 * FilterPanel — slides up from bottom as a modal sheet.
 * Handles filter selection (category, subcategory, color, season, occasion, brand, status)
 * and sort order. Resolves junction table item IDs before committing.
 *
 * Sort, Status, Category, Subcategory, Season, Occasion, Brand use PickerTrigger rows (tap → inner sheet).
 * Color uses a collapsible chip row (inline single-select).
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
  useWindowDimensions,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { FontSize, FontWeight, Palette, Radius, Spacing } from '@/constants/tokens';
import { PhosphorIcon } from '@/components/PhosphorIcon';
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
import { contrastingTextColor } from '@/utils/color';

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

type InnerSheet = 'sort' | 'status' | 'category' | 'subcategory' | 'season' | 'occasion' | 'brand' | null;

type Props = {
  visible: boolean;
  onClose: () => void;
  currentFilters: ActiveFilters;
  currentSort: SortKey;
  onApply: (filters: ActiveFilters, sort: SortKey) => void;
};

// ---------------------------------------------------------------------------
// Component
/**
 * Presents a modal bottom sheet that lets the user select filters and a sort order for the closet view, then apply or clear them.
 *
 * Sort, Status, Category, Subcategory, Season, Occasion, and Brand each open an inner picker sheet.
 * Color uses a collapsible inline chip row (single-select).
 *
 * @param visible - Whether the panel is visible
 * @param onClose - Callback invoked to close the panel
 * @param currentFilters - Currently applied filter values used to initialize the draft when the panel opens
 * @param currentSort - Currently applied sort key used to initialize the draft sort when the panel opens
 * @param onApply - Callback invoked with the finalized filters (including resolved `junctionItemIds` when applicable) and selected sort when the user applies changes
 * @returns The rendered FilterPanel component
 */

export function FilterPanel({ visible, onClose, currentFilters, currentSort, onApply }: Props) {
  const { accent } = useAccent();
  const insets = useSafeAreaInsets();
  const { height: screenHeight } = useWindowDimensions();
  const slideAnim = useRef(new Animated.Value(0)).current;

  // Local draft state — committed only on Apply
  const [draft, setDraft] = useState<ActiveFilters>(currentFilters);
  const [draftSort, setDraftSort] = useState<SortKey>(currentSort);

  // Which inner picker sheet is open
  const [innerSheet, setInnerSheet] = useState<InnerSheet>(null);

  // Color section collapsed state
  const [colorExpanded, setColorExpanded] = useState(false);

  // Lookup data
  const [categories, setCategories] = useState<Category[]>([]);
  const [subcategories, setSubcategories] = useState<Subcategory[]>([]);
  const [seasons, setSeasons] = useState<Season[]>([]);
  const [occasions, setOccasions] = useState<Occasion[]>([]);
  const [colors, setColors] = useState<Color[]>([]);
  const [brands, setBrands] = useState<string[]>([]);

  useEffect(() => {
    (async () => {
      try {
        const db = await getDatabase();
        const [cats, seas, occs, cols, brnds] = await Promise.all([
          getCategories(db),
          getSeasons(db),
          getOccasions(db),
          getColors(db),
          getDistinctBrands(db),
        ]);
        setCategories(cats);
        setSeasons([...seas].sort((a, b) => a.name.localeCompare(b.name)));
        setOccasions([...occs].sort((a, b) => a.name.localeCompare(b.name)));
        setColors(cols);
        setBrands(brnds);
      } catch (e) {
        console.error('[FilterPanel] load lookup data', e);
      }
    })();
  }, []);

  // Reload subcategories when draft category changes
  useEffect(() => {
    if (!draft.categoryId) { setSubcategories([]); return; }
    getDatabase()
      .then((db) => getSubcategories(db, draft.categoryId!).then(setSubcategories))
      .catch((e) => { console.error('[FilterPanel] subcategories', e); setSubcategories([]); });
  }, [draft.categoryId]);

  // Sync draft when panel opens with current values
  useEffect(() => {
    if (visible) {
      setDraft(currentFilters);
      setDraftSort(currentSort);
      setInnerSheet(null);
      setColorExpanded(false);
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
    outputRange: [screenHeight, 0],
  });

  // Derived display labels
  const selectedCategoryName = categories.find((c) => c.id === draft.categoryId)?.name ?? null;
  const selectedSubcategoryName = subcategories.find((s) => s.id === draft.subcategoryId)?.name ?? null;

  return (
    <>
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
          <FilterPickerTrigger
            label="Sort By"
            value={SORT_LABELS[draftSort]}
            onPress={() => setInnerSheet('sort')}
          />

          {/* Status */}
          <FilterPickerTrigger
            label="Status"
            value={draft.status ?? 'Any'}
            onPress={() => setInnerSheet('status')}
          />

          {/* Category */}
          <FilterPickerTrigger
            label="Category"
            value={selectedCategoryName ?? 'Any'}
            onPress={() => setInnerSheet('category')}
          />

          {/* Subcategory — only when a category is selected and subcategories exist */}
          {draft.categoryId !== null && subcategories.length > 0 && (
            <FilterPickerTrigger
              label="Subcategory"
              value={selectedSubcategoryName ?? 'Any'}
              onPress={() => setInnerSheet('subcategory')}
            />
          )}

          {/* Season */}
          <FilterPickerTrigger
            label="Season"
            value={seasons.find((s) => s.id === draft.seasonId)?.name ?? 'Any'}
            onPress={() => setInnerSheet('season')}
          />

          {/* Occasion */}
          <FilterPickerTrigger
            label="Occasion"
            value={occasions.find((o) => o.id === draft.occasionId)?.name ?? 'Any'}
            onPress={() => setInnerSheet('occasion')}
          />

          {/* Color — collapsible */}
          <Pressable
            style={styles.colorToggle}
            onPress={() => setColorExpanded((v) => !v)}
            accessibilityRole="button"
            accessibilityState={{ expanded: colorExpanded }}
          >
            <Text style={styles.pickerTriggerLabel}>Color</Text>
            <View style={styles.pickerTriggerRight}>
              {draft.colorId !== null && (
                <Text style={styles.pickerTriggerValue} numberOfLines={1}>
                  {colors.find((c) => c.id === draft.colorId)?.name ?? ''}
                </Text>
              )}
              <PhosphorIcon name="caret-up-down" size={14} color={Palette.textSecondary} />
            </View>
          </Pressable>
          {colorExpanded && (
            <View style={styles.colorChipsContainer}>
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
            </View>
          )}

          {/* Brand */}
          {brands.length > 0 && (
            <FilterPickerTrigger
              label="Brand"
              value={draft.brand ?? 'Any'}
              onPress={() => setInnerSheet('brand')}
            />
          )}
        </ScrollView>
      </Animated.View>
    </Modal>

    {/* Inner picker sheets — rendered outside the outer Modal to avoid iOS nested-modal issues */}
    <FilterPickerSheet
        visible={innerSheet === 'sort'}
        title="Sort By"
        options={Object.entries(SORT_LABELS).map(([k, v]) => ({ value: k, label: v }))}
        selected={draftSort}
        onSelect={(v) => { if (v !== null) setDraftSort(v as SortKey); setInnerSheet(null); }}
        onClose={() => setInnerSheet(null)}
        accentPrimary={accent.primary}
        allowDeselect={false}
      />
      <FilterPickerSheet
        visible={innerSheet === 'status'}
        title="Status"
        options={[
          { value: 'Active',  label: 'Active'  },
          { value: 'Sold',    label: 'Sold'    },
          { value: 'Donated', label: 'Donated' },
          { value: 'Lost',    label: 'Lost'    },
        ]}
        selected={draft.status ?? null}
        onSelect={(v) => { setDraftFilter('status', v as ActiveFilters['status']); setInnerSheet(null); }}
        onClose={() => setInnerSheet(null)}
        accentPrimary={accent.primary}
        allowDeselect
      />
      <FilterPickerSheet
        visible={innerSheet === 'category'}
        title="Category"
        options={categories.map((c) => ({ value: String(c.id), label: c.name, icon: c.icon ?? undefined }))}
        selected={draft.categoryId !== null ? String(draft.categoryId) : null}
        onSelect={(v) => {
          setDraft((d) => ({ ...d, categoryId: v !== null ? Number(v) : null, subcategoryId: null }));
          setInnerSheet(null);
        }}
        onClose={() => setInnerSheet(null)}
        accentPrimary={accent.primary}
        allowDeselect
      />
      <FilterPickerSheet
        visible={innerSheet === 'subcategory'}
        title="Subcategory"
        options={subcategories.map((s) => ({ value: String(s.id), label: s.name }))}
        selected={draft.subcategoryId !== null ? String(draft.subcategoryId) : null}
        onSelect={(v) => {
          setDraftFilter('subcategoryId', v !== null ? Number(v) : null);
          setInnerSheet(null);
        }}
        onClose={() => setInnerSheet(null)}
        accentPrimary={accent.primary}
        allowDeselect
      />
      <FilterPickerSheet
        visible={innerSheet === 'brand'}
        title="Brand"
        options={brands.map((b) => ({ value: b, label: b }))}
        selected={draft.brand}
        onSelect={(v) => { setDraftFilter('brand', v); setInnerSheet(null); }}
        onClose={() => setInnerSheet(null)}
        accentPrimary={accent.primary}
        allowDeselect
      />
      <FilterPickerSheet
        visible={innerSheet === 'season'}
        title="Season"
        options={seasons.map((s) => ({ value: String(s.id), label: s.name, icon: s.icon ?? undefined }))}
        selected={draft.seasonId !== null ? String(draft.seasonId) : null}
        onSelect={(v) => {
          setDraftFilter('seasonId', v !== null ? Number(v) : null);
          setInnerSheet(null);
        }}
        onClose={() => setInnerSheet(null)}
        accentPrimary={accent.primary}
        allowDeselect
      />
      <FilterPickerSheet
        visible={innerSheet === 'occasion'}
        title="Occasion"
        options={occasions.map((o) => ({ value: String(o.id), label: o.name, icon: o.icon ?? undefined }))}
        selected={draft.occasionId !== null ? String(draft.occasionId) : null}
        onSelect={(v) => {
          setDraftFilter('occasionId', v !== null ? Number(v) : null);
          setInnerSheet(null);
        }}
        onClose={() => setInnerSheet(null)}
        accentPrimary={accent.primary}
        allowDeselect
      />
    </>
  );
}

// ---------------------------------------------------------------------------
// FilterPickerTrigger — row that opens an inner sheet
// ---------------------------------------------------------------------------

function FilterPickerTrigger({
  label,
  value,
  onPress,
}: {
  label: string;
  value: string;
  onPress: () => void;
}) {
  return (
    <Pressable style={styles.pickerTrigger} onPress={onPress} accessibilityRole="button">
      <Text style={styles.pickerTriggerLabel}>{label}</Text>
      <View style={styles.pickerTriggerRight}>
        <Text style={styles.pickerTriggerValue} numberOfLines={1}>{value}</Text>
        <PhosphorIcon name="caret-right" size={18} color={Palette.textDisabled} />
      </View>
    </Pressable>
  );
}

// ---------------------------------------------------------------------------
// FilterPickerSheet — inner bottom-sheet for single-value selection
// ---------------------------------------------------------------------------

type PickerOption = { value: string; label: string; icon?: string };

function FilterPickerSheet({
  visible,
  title,
  options,
  selected,
  onSelect,
  onClose,
  accentPrimary,
  allowDeselect,
}: {
  visible: boolean;
  title: string;
  options: PickerOption[];
  selected: string | null;
  onSelect: (value: string | null) => void;
  onClose: () => void;
  accentPrimary: string;
  allowDeselect: boolean;
}) {
  const insets = useSafeAreaInsets();
  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <Pressable style={styles.innerBackdrop} onPress={onClose} accessibilityRole="button" accessibilityLabel="Close" />
      <View style={styles.innerSheet}>
        <View style={styles.innerHandle} />
        <Text style={styles.innerTitle}>{title}</Text>
        <ScrollView style={styles.innerScroll} showsVerticalScrollIndicator={false}>
          {options.map((opt, i) => {
            const active = opt.value === selected;
            return (
              <Pressable
                key={opt.value}
                style={[styles.innerOption, i < options.length - 1 && styles.innerOptionBorder]}
                onPress={() => onSelect(allowDeselect && active ? null : opt.value)}
                accessibilityRole="radio"
                accessibilityState={{ selected: active }}
              >
                <View style={styles.innerOptionLeft}>
                  {opt.icon ? (
                    <PhosphorIcon name={opt.icon} size={20} color={active ? accentPrimary : Palette.textSecondary} />
                  ) : null}
                  <Text style={[styles.innerOptionText, active && { color: accentPrimary, fontWeight: FontWeight.semibold }]}>
                    {opt.label}
                  </Text>
                </View>
                {active && <PhosphorIcon name="check" size={16} color={accentPrimary} />}
              </Pressable>
            );
          })}
          <View style={{ height: Math.max(Spacing[4], insets.bottom) }} />
        </ScrollView>
      </View>
    </Modal>
  );
}

// ---------------------------------------------------------------------------
// Sub-components

type ChipItem = { id: string | number; name: string };

/**
 * Renders a horizontal row of selectable chips for the given items.
 */
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
            <Text style={[styles.chipText, selected && styles.chipTextSelected, selected && { color: contrastingTextColor(accent) }]}>
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
    backgroundColor: Palette.overlay,
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

  // PickerTrigger row (Sort, Status, Category, Subcategory, Brand)
  pickerTrigger: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: Spacing[4],
    borderBottomWidth: 1,
    borderBottomColor: Palette.borderMuted,
  },
  pickerTriggerLabel: {
    color: Palette.textPrimary,
    fontSize: FontSize.md,
  },
  pickerTriggerRight: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing[2],
    flexShrink: 0,
    maxWidth: '55%',
  },
  pickerTriggerValue: {
    color: Palette.textSecondary,
    fontSize: FontSize.md,
    textAlign: 'right',
    flexShrink: 1,
  },

  // Color collapsible
  colorToggle: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: Spacing[4],
    borderBottomWidth: 1,
    borderBottomColor: Palette.borderMuted,
  },
  colorChipsContainer: {
    paddingTop: Spacing[3],
    paddingBottom: Spacing[2],
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
    fontWeight: FontWeight.semibold,
  },
  colorDot: {
    width: 10,
    height: 10,
    borderRadius: Radius.full,
    borderWidth: 1,
    borderColor: Palette.dotBorder,
  },

  // Inner picker sheet (Sort/Status/Category/Subcategory/Brand)
  innerBackdrop: {
    flex: 1,
    backgroundColor: Palette.overlay,
  },
  innerSheet: {
    backgroundColor: Palette.surface1,
    borderTopLeftRadius: Radius.xl,
    borderTopRightRadius: Radius.xl,
    borderWidth: 1,
    borderColor: Palette.border,
    paddingTop: Spacing[2],
    maxHeight: '70%',
  },
  innerHandle: {
    width: 36,
    height: 4,
    borderRadius: Radius.full,
    backgroundColor: Palette.border,
    alignSelf: 'center',
    marginBottom: Spacing[3],
  },
  innerTitle: {
    color: Palette.textSecondary,
    fontSize: FontSize.sm,
    fontWeight: FontWeight.semibold,
    textTransform: 'uppercase',
    letterSpacing: 0.6,
    paddingHorizontal: Spacing[4],
    paddingBottom: Spacing[3],
  },
  innerScroll: {
    flexShrink: 1,
  },
  innerOption: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: Spacing[4],
    paddingVertical: Spacing[4],
  },
  innerOptionBorder: {
    borderBottomWidth: 1,
    borderBottomColor: Palette.borderMuted,
  },
  innerOptionLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing[3],
  },
  innerOptionText: {
    color: Palette.textPrimary,
    fontSize: FontSize.md,
  },
});
