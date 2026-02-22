/**
 * Edit Outfit screen — /outfit/[id]/edit
 *
 * Same two-step flow as new.tsx (pick items → set name) but pre-populated
 * with existing outfit data. Saves via updateOutfit. Delete lives here.
 */

import { Image } from 'expo-image';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useState } from 'react';
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
import { deleteOutfit, getOutfitWithItems, updateOutfit } from '@/db/queries';
import { ClothingItemWithMeta } from '@/db/types';
import { useClothingItems } from '@/hooks/useClothingItems';
import { contrastingTextColor } from '@/utils/color';
import { toImageUri } from '@/utils/image';

type Step = 'pick' | 'name';

const CARD_GAP = Spacing[2];
const GRID_COLUMNS = 3;
const GRID_PADDING = Spacing[3];

export default function EditOutfitScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const outfitId = parseInt(id ?? '', 10);
  const { accent } = useAccent();
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const { width: screenWidth } = useWindowDimensions();

  const { items, loading: itemsLoading } = useClothingItems();

  const [step, setStep] = useState<Step>('pick');
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [outfitName, setOutfitName] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);

  const [loadError, setLoadError] = useState<string | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);

  // Load existing outfit to pre-fill state
  useEffect(() => {
    if (!Number.isFinite(outfitId) || outfitId <= 0) return;
    (async () => {
      try {
        const db = await getDatabase();
        const outfit = await getOutfitWithItems(db, outfitId);
        if (!outfit) {
          setLoadError('Outfit not found.');
          return;
        }
        setOutfitName(outfit.name ?? '');
        setSelected(new Set(outfit.items.map((i) => i.id)));
        setLoaded(true);
      } catch (e) {
        setLoadError(String(e));
      }
    })();
  }, [outfitId]);

  // Active items — include items that were originally in the outfit even if now non-Active
  // so they stay selectable when editing
  const activeItems = useMemo(
    () => items.filter((i) => i.status === 'Active' || selected.has(i.id)),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [items] // selected is only used for the initial filter; don't re-derive on every toggle
  );

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

  const filteredItems = useMemo(
    () =>
      selectedCategory
        ? activeItems.filter((i) => i.category_name === selectedCategory)
        : activeItems,
    [activeItems, selectedCategory]
  );

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
      Alert.alert('No items selected', 'An outfit needs at least one item.');
      return;
    }
    setSaving(true);
    try {
      const db = await getDatabase();
      await updateOutfit(
        db,
        outfitId,
        { name: outfitName.trim() || null, notes: null },
        [...selected]
      );
      router.back();
    } catch (e) {
      Alert.alert('Error', String(e));
      setSaving(false);
    }
  };

  const handleDelete = useCallback(() => {
    Alert.alert('Delete outfit?', 'This will remove the outfit. Logged history is kept.', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete', style: 'destructive',
        onPress: async () => {
          if (deleting) return;
          setDeleting(true);
          try {
            const db = await getDatabase();
            await deleteOutfit(db, outfitId);
            router.replace('/(tabs)/outfits');
          } catch (e) {
            console.error('[handleDelete]', e);
            Alert.alert('Error', 'Could not delete the outfit. Please try again.');
            setDeleting(false);
          }
        },
      },
    ]);
  }, [deleting, outfitId, router]);

  if (!Number.isFinite(outfitId) || outfitId <= 0 || loadError) {
    return (
      <View style={[styles.container, { paddingTop: insets.top }]}>
        <View style={styles.center}>
          <Text style={styles.placeholderText}>{loadError ?? 'Outfit not found.'}</Text>
        </View>
      </View>
    );
  }

  if (!loaded || itemsLoading) {
    return (
      <View style={[styles.container, { paddingTop: insets.top }]}>
        <View style={styles.center}>
          <ActivityIndicator color={accent.primary} />
        </View>
      </View>
    );
  }

  return (
    <View style={[styles.container, { paddingTop: insets.top }]}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity
          onPress={() => (step === 'name' ? setStep('pick') : router.back())}
          hitSlop={10}
          style={styles.headerBackBtn}
        >
          <PhosphorIcon name="caret-left" size={18} color={Palette.textSecondary} />
          <Text style={styles.headerBack}>
            {step === 'name' ? 'Back' : 'Cancel'}
          </Text>
        </TouchableOpacity>

        <Text style={styles.headerTitle}>
          {step === 'pick' ? 'Edit Items' : 'Edit Name'}
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

      {/* Category pill bar */}
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
            <Text style={[styles.pillText, !selectedCategory && { color: accent.primary }]}>
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
          selected={selected}
          onToggle={toggleItem}
          accent={accent.primary}
          cardWidth={cardWidth}
          isFiltered={selectedCategory !== null}
        />
      ) : (
        <NameStep
          name={outfitName}
          onChangeName={setOutfitName}
          selectedItems={activeItems.filter((i) => selected.has(i.id))}
          onDelete={handleDelete}
          deleting={deleting}
        />
      )}
    </View>
  );
}

// ---------------------------------------------------------------------------
// Item picker grid (same as new.tsx, no loading state needed here — parent handles it)

function ItemPicker({
  items,
  selected,
  onToggle,
  accent,
  cardWidth,
  isFiltered,
}: {
  items: ClothingItemWithMeta[];
  selected: Set<number>;
  onToggle: (id: number) => void;
  accent: string;
  cardWidth: number;
  isFiltered: boolean;
}) {
  if (items.length === 0) {
    return (
      <View style={styles.center}>
        <Text style={styles.emptyText}>
          {isFiltered ? 'No items in this category.' : 'No active items in your closet yet.'}
        </Text>
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
// Name step with Delete action

function NameStep({
  name,
  onChangeName,
  selectedItems,
  onDelete,
  deleting,
}: {
  name: string;
  onChangeName: (v: string) => void;
  selectedItems: ClothingItemWithMeta[];
  onDelete: () => void;
  deleting: boolean;
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

      {/* Delete — placed at the bottom to keep it out of the way */}
      <TouchableOpacity
        style={styles.deleteButton}
        onPress={onDelete}
        disabled={deleting}
        activeOpacity={0.75}
      >
        <Text style={styles.deleteButtonText}>{deleting ? 'Deleting…' : 'Delete Outfit'}</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

// ---------------------------------------------------------------------------
// Helpers

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

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Palette.surface0,
  },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  placeholderText: {
    color: Palette.textSecondary,
    fontSize: FontSize.md,
  },

  // Header
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

  emptyText: {
    color: Palette.textSecondary,
    fontSize: FontSize.md,
    textAlign: 'center',
    paddingHorizontal: Spacing[6],
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

  // Delete
  deleteButton: {
    marginTop: Spacing[6],
    paddingVertical: Spacing[3],
    borderRadius: Radius.md,
    borderWidth: 1,
    borderColor: Palette.error,
    alignItems: 'center',
  },
  deleteButtonText: {
    color: Palette.error,
    fontSize: FontSize.md,
    fontWeight: FontWeight.medium,
  },
});
