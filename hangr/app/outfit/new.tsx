/**
 * Outfit Builder
 *
 * Multi-step: pick items from closet → set optional name → save.
 * No routing params — always creates a new outfit.
 */

import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  FlatList,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
  useWindowDimensions,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { FontSize, FontWeight, Palette, Radius, Spacing } from '@/constants/tokens';
import { PhosphorIcon } from '@/components/PhosphorIcon';
import { useAccent } from '@/context/AccentContext';
import { getDatabase } from '@/db';
import { insertOutfit } from '@/db/queries';
import { ClothingItemWithMeta } from '@/db/types';
import { useClothingItems } from '@/hooks/useClothingItems';
import { contrastingTextColor } from '@/utils/color';
import { toImageUri } from '@/utils/image';

type Step = 'pick' | 'name';

const CARD_GAP = Spacing[2];
const GRID_COLUMNS = 3;
const GRID_PADDING = Spacing[3];

/**
 * Multi-step screen to create a new outfit by selecting items, optionally naming it, and saving it.
 *
 * Presents a "pick" step for choosing active closet items (with category filter pills) and a "name"
 * step for entering an optional outfit name with a preview of selected items. On save, the outfit is
 * persisted and the UI navigates to the newly created outfit.
 *
 * @returns The JSX element rendering the new-outfit creation screen.
 */
export default function NewOutfitScreen() {
  const { accent } = useAccent();
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const { width: screenWidth } = useWindowDimensions();

  const { items, loading } = useClothingItems();

  const [step, setStep] = useState<Step>('pick');
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [outfitName, setOutfitName] = useState('');
  const [saving, setSaving] = useState(false);
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);

  // Only show Active items in the picker
  const activeItems = useMemo(() => items.filter((i) => i.status === 'Active'), [items]);

  // Derive distinct categories from active items (preserves insertion order)
  const categories = useMemo(() => {
    const seen = new Set<string>();
    const result: string[] = [];
    for (const item of activeItems) {
      if (item.category_name && !seen.has(item.category_name)) {
        seen.add(item.category_name);
        result.push(item.category_name);
      }
    }
    return result;
  }, [activeItems]);

  // Items shown in the picker grid — filtered by selected category, or all
  const filteredItems = useMemo(
    () =>
      selectedCategory
        ? activeItems.filter((i) => i.category_name === selectedCategory)
        : activeItems,
    [activeItems, selectedCategory]
  );

  // Card width matching the closet screen grid math
  const cardWidth =
    (screenWidth - GRID_PADDING * 2 - CARD_GAP * (GRID_COLUMNS - 1)) / GRID_COLUMNS;

  const toggleItem = (id: number) => {
    setSelected((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  };

  const handleSave = async () => {
    if (selected.size === 0) {
      Alert.alert('No items selected', 'Pick at least one item for the outfit.');
      return;
    }
    setSaving(true);
    try {
      const db = await getDatabase();
      const outfitId = await insertOutfit(
        db,
        { name: outfitName.trim() || null, notes: null },
        [...selected]
      );
      router.replace(`/outfit/${outfitId}` as any);
    } catch (e) {
      Alert.alert('Error', String(e));
      setSaving(false);
    }
  };

  return (
    <View style={[styles.container, { paddingTop: insets.top }]}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => (step === 'name' ? setStep('pick') : router.back())} hitSlop={10} style={styles.headerBackBtn}>
          {step === 'name' ? (
            <>
              <PhosphorIcon name="caret-left" size={18} color={Palette.textSecondary} />
              <Text style={styles.headerBack}>Back</Text>
            </>
          ) : (
            <Text style={styles.headerBack}>Cancel</Text>
          )}
        </TouchableOpacity>

        <Text style={styles.headerTitle}>
          {step === 'pick' ? 'Pick Items' : 'Name Outfit'}
        </Text>

        {step === 'pick' ? (
          <TouchableOpacity
            onPress={() => {
              if (selected.size === 0) {
                Alert.alert('No items selected', 'Pick at least one item.');
                return;
              }
              setStep('name');
            }}
            hitSlop={10}
          >
            <Text style={[styles.headerAction, { color: accent.primary }]}>
              Next ({selected.size})
            </Text>
          </TouchableOpacity>
        ) : (
          <TouchableOpacity onPress={handleSave} hitSlop={10} disabled={saving}>
            {saving ? (
              <ActivityIndicator color={accent.primary} />
            ) : (
              <Text style={[styles.headerAction, { color: accent.primary }]}>Save</Text>
            )}
          </TouchableOpacity>
        )}
      </View>

      {/* Category pill bar — only shown on pick step with multiple categories */}
      {step === 'pick' && categories.length > 1 && (
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.pillBar}
          style={styles.pillBarWrapper}
        >
          <TouchableOpacity
            style={[
              styles.pill,
              !selectedCategory && {
                backgroundColor: accent.subtle,
                borderColor: accent.primary,
              },
            ]}
            onPress={() => setSelectedCategory(null)}
            activeOpacity={0.75}
          >
            <Text
              style={[
                styles.pillText,
                !selectedCategory && { color: accent.primary },
              ]}
            >
              All
            </Text>
          </TouchableOpacity>

          {categories.map((cat) => {
            const isActive = selectedCategory === cat;
            return (
              <TouchableOpacity
                key={cat}
                style={[
                  styles.pill,
                  isActive && {
                    backgroundColor: accent.subtle,
                    borderColor: accent.primary,
                  },
                ]}
                onPress={() => setSelectedCategory(isActive ? null : cat)}
                activeOpacity={0.75}
              >
                <Text style={[styles.pillText, isActive && { color: accent.primary }]}>
                  {cat}
                </Text>
              </TouchableOpacity>
            );
          })}
        </ScrollView>
      )}

      {step === 'pick' ? (
        <ItemPicker
          items={filteredItems}
          loading={loading}
          selected={selected}
          onToggle={toggleItem}
          accent={accent.primary}
          cardWidth={cardWidth}
        />
      ) : (
        <NameStep
          name={outfitName}
          onChangeName={setOutfitName}
          selectedItems={activeItems.filter((i) => selected.has(i.id))}
        />
      )}
    </View>
  );
}

// ---------------------------------------------------------------------------
// Step 1 — Item picker grid
/**
 * Render a 3-column grid UI that lets the user pick clothing items, with built-in loading and empty states.
 *
 * @param items - Array of clothing items (with metadata) to display as selectable tiles.
 * @param loading - When true, show a centered activity indicator instead of the grid.
 * @param selected - Set of item IDs currently selected; selected tiles show a highlighted border and checkmark.
 * @param onToggle - Callback invoked with an item ID when a tile is pressed to toggle its selection.
 * @param accent - Color used for selection highlights and activity indicator.
 * @param cardWidth - Explicit card width computed from screen width and grid constants.
 * @returns A React element containing either a loading indicator, an empty message, or the selectable item grid.
 */

function ItemPicker({
  items,
  loading,
  selected,
  onToggle,
  accent,
  cardWidth,
}: {
  items: ClothingItemWithMeta[];
  loading: boolean;
  selected: Set<number>;
  onToggle: (id: number) => void;
  accent: string;
  cardWidth: number;
}) {
  if (loading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator color={accent} />
      </View>
    );
  }

  if (items.length === 0) {
    return (
      <View style={styles.center}>
        <Text style={styles.emptyText}>No active items in your closet yet.</Text>
      </View>
    );
  }

  return (
    <FlatList
      data={items}
      keyExtractor={(i) => String(i.id)}
      numColumns={3}
      contentContainerStyle={styles.pickerGrid}
      columnWrapperStyle={styles.pickerRow}
      renderItem={({ item }) => {
        const isSelected = selected.has(item.id);
        return (
          <Pressable
            style={[
              styles.pickerCell,
              { width: cardWidth },
              isSelected && { borderColor: accent, borderWidth: 2.5 },
            ]}
            onPress={() => onToggle(item.id)}
          >
            {item.image_path ? (
              <Image
                source={{ uri: toImageUri(item.image_path)! }}
                style={styles.pickerImage}
                contentFit="cover"
              />
            ) : (
              <View style={styles.pickerPlaceholder}>
                <Text style={styles.pickerEmoji}>{categoryEmoji(item.category_name)}</Text>
              </View>
            )}
            {isSelected && (
              <View style={[styles.checkmark, { backgroundColor: accent }]}>
                <PhosphorIcon name="check" size={12} color={contrastingTextColor(accent)} />
              </View>
            )}
            <Text style={styles.pickerLabel} numberOfLines={1}>{item.name}</Text>
          </Pressable>
        );
      }}
    />
  );
}

// ---------------------------------------------------------------------------
// Step 2 — Name + preview
/**
 * Renders the "name" step of the outfit creation flow: an optional outfit name input and a thumbnail preview of selected items.
 *
 * @param name - The current text value of the outfit name input.
 * @param onChangeName - Callback invoked with the new name when the input changes.
 * @param selectedItems - Array of clothing items to display as thumbnails in the preview strip.
 * @returns The UI for the name-and-preview step, including the name TextInput and a horizontal strip of item thumbnails.
 */

function NameStep({
  name,
  onChangeName,
  selectedItems,
}: {
  name: string;
  onChangeName: (v: string) => void;
  selectedItems: ClothingItemWithMeta[];
}) {
  return (
    <ScrollView contentContainerStyle={styles.nameContent} keyboardShouldPersistTaps="handled">
      <Text style={styles.nameLabel}>Outfit Name (optional)</Text>
      <TextInput
        style={styles.nameInput}
        placeholder="e.g. Monday fit, Date night…"
        placeholderTextColor={Palette.textDisabled}
        value={name}
        onChangeText={onChangeName}
        autoFocus
        returnKeyType="done"
      />

      <Text style={styles.previewLabel}>
        {selectedItems.length} item{selectedItems.length !== 1 ? 's' : ''}
      </Text>

      <View style={styles.previewStrip}>
        {selectedItems.map((item) => (
          <View key={item.id} style={styles.previewThumb}>
            {item.image_path ? (
              <Image
                source={{ uri: toImageUri(item.image_path)! }}
                style={styles.previewThumbImage}
                contentFit="cover"
              />
            ) : (
              <View style={styles.previewThumbPlaceholder}>
                <Text style={styles.previewThumbEmoji}>{categoryEmoji(item.category_name)}</Text>
              </View>
            )}
          </View>
        ))}
      </View>
    </ScrollView>
  );
}

// ---------------------------------------------------------------------------
// Helpers
/**
 * Maps a clothing category name to a representative emoji.
 *
 * @param name - The clothing category name (or `null`) to map to an emoji
 * @returns An emoji corresponding to the category; returns 🧺 for unknown or `null` categories
 */

function categoryEmoji(name: string | null): string {
  switch (name) {
    case 'Tops':                  return '👕';
    case 'Bottoms':               return '👖';
    case 'Outerwear':             return '🧥';
    case 'Dresses & Jumpsuits':   return '👗';
    case 'Footwear':              return '👟';
    case 'Accessories':           return '⌚';
    case 'Bags':                  return '👜';
    case 'Activewear':            return '🏃';
    case 'Underwear & Intimates': return '🧦';
    case 'Swimwear':              return '🩱';
    default:                      return '🧺';
  }
}

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Palette.surface0,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: Spacing[4],
    paddingVertical: Spacing[3],
    borderBottomWidth: 1,
    borderBottomColor: Palette.border,
  },
  headerBackBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing[1],
    minWidth: 72,
  },
  headerBack: {
    color: Palette.textSecondary,
    fontSize: FontSize.md,
  },
  headerTitle: {
    color: Palette.textPrimary,
    fontSize: FontSize.md,
    fontWeight: FontWeight.semibold,
  },
  headerAction: {
    fontSize: FontSize.md,
    fontWeight: FontWeight.semibold,
    minWidth: 72,
    textAlign: 'right',
  },

  // Category pill bar
  pillBarWrapper: {
    borderBottomWidth: 1,
    borderBottomColor: Palette.border,
    flexGrow: 0,
  },
  pillBar: {
    paddingHorizontal: Spacing[3],
    paddingVertical: Spacing[2],
    gap: Spacing[2],
  },
  pill: {
    paddingHorizontal: Spacing[3],
    paddingVertical: Spacing[2],
    borderRadius: Radius.full,
    borderWidth: 1,
    borderColor: Palette.border,
    backgroundColor: Palette.surface2,
  },
  pillText: {
    color: Palette.textSecondary,
    fontSize: FontSize.sm,
    fontWeight: FontWeight.medium,
  },

  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyText: {
    color: Palette.textSecondary,
    fontSize: FontSize.md,
    textAlign: 'center',
  },

  // Picker grid
  pickerGrid: {
    padding: GRID_PADDING,
    paddingBottom: Spacing[16],
  },
  pickerRow: {
    gap: CARD_GAP,
    marginBottom: CARD_GAP,
  },
  pickerCell: {
    borderRadius: Radius.sm,
    overflow: 'hidden',
    backgroundColor: Palette.surface1,
    borderWidth: 2,
    borderColor: 'transparent',
  },
  pickerImage: {
    width: '100%',
    aspectRatio: 3 / 4,
  },
  pickerPlaceholder: {
    width: '100%',
    aspectRatio: 3 / 4,
    backgroundColor: Palette.surface2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pickerEmoji: {
    fontSize: 28,
  },
  checkmark: {
    position: 'absolute',
    top: Spacing[1],
    right: Spacing[1],
    width: 20,
    height: 20,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pickerLabel: {
    color: Palette.textPrimary,
    fontSize: FontSize.xs,
    padding: Spacing[1],
    paddingHorizontal: Spacing[2],
  },

  // Name step
  nameContent: {
    padding: Spacing[5],
    gap: Spacing[3],
  },
  nameLabel: {
    color: Palette.textSecondary,
    fontSize: FontSize.sm,
    fontWeight: FontWeight.medium,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  nameInput: {
    backgroundColor: Palette.surface2,
    borderRadius: Radius.md,
    borderWidth: 1,
    borderColor: Palette.border,
    padding: Spacing[3],
    color: Palette.textPrimary,
    fontSize: FontSize.md,
  },
  previewLabel: {
    color: Palette.textSecondary,
    fontSize: FontSize.sm,
    fontWeight: FontWeight.medium,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginTop: Spacing[4],
  },
  previewStrip: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing[2],
  },
  previewThumb: {
    width: 64,
    height: 80,
    borderRadius: Radius.sm,
    overflow: 'hidden',
    backgroundColor: Palette.surface2,
  },
  previewThumbImage: {
    width: '100%',
    height: '100%',
  },
  previewThumbPlaceholder: {
    width: '100%',
    height: '100%',
    alignItems: 'center',
    justifyContent: 'center',
  },
  previewThumbEmoji: {
    fontSize: 24,
  },
});
