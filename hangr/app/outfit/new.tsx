/**
 * Outfit Builder
 *
 * Multi-step: pick items from closet → set optional name → save.
 * No routing params — always creates a new outfit.
 */

import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { useState } from 'react';
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
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { FontSize, FontWeight, Palette, Radius, Spacing } from '@/constants/tokens';
import { useAccent } from '@/context/AccentContext';
import { getDatabase } from '@/db';
import { insertOutfit } from '@/db/queries';
import { ClothingItemWithMeta } from '@/db/types';
import { useClothingItems } from '@/hooks/useClothingItems';

type Step = 'pick' | 'name';

export default function NewOutfitScreen() {
  const { accent } = useAccent();
  const insets = useSafeAreaInsets();
  const router = useRouter();

  const { items, loading } = useClothingItems();

  const [step, setStep] = useState<Step>('pick');
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [outfitName, setOutfitName] = useState('');
  const [saving, setSaving] = useState(false);

  // Only show Active items in the picker
  const activeItems = items.filter((i) => i.status === 'Active');

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
      router.replace(`/outfit/${outfitId}`);
    } catch (e) {
      Alert.alert('Error', String(e));
      setSaving(false);
    }
  };

  return (
    <View style={[styles.container, { paddingTop: insets.top }]}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => (step === 'name' ? setStep('pick') : router.back())} hitSlop={10}>
          <Text style={styles.headerBack}>{step === 'name' ? '← Back' : 'Cancel'}</Text>
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

      {step === 'pick' ? (
        <ItemPicker
          items={activeItems}
          loading={loading}
          selected={selected}
          onToggle={toggleItem}
          accent={accent.primary}
        />
      ) : (
        <NameStep
          name={outfitName}
          onChangeName={setOutfitName}
          selectedItems={activeItems.filter((i) => selected.has(i.id))}
          accent={accent.primary}
        />
      )}
    </View>
  );
}

// ---------------------------------------------------------------------------
// Step 1 — Item picker grid
// ---------------------------------------------------------------------------

function ItemPicker({
  items,
  loading,
  selected,
  onToggle,
  accent,
}: {
  items: ClothingItemWithMeta[];
  loading: boolean;
  selected: Set<number>;
  onToggle: (id: number) => void;
  accent: string;
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
            style={[styles.pickerCell, isSelected && { borderColor: accent, borderWidth: 2.5 }]}
            onPress={() => onToggle(item.id)}
          >
            {item.image_path ? (
              <Image
                source={{ uri: item.image_path }}
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
                <Text style={styles.checkmarkText}>✓</Text>
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
// ---------------------------------------------------------------------------

function NameStep({
  name,
  onChangeName,
  selectedItems,
  accent,
}: {
  name: string;
  onChangeName: (v: string) => void;
  selectedItems: ClothingItemWithMeta[];
  accent: string;
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
                source={{ uri: item.image_path }}
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
// ---------------------------------------------------------------------------

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
  headerBack: {
    color: Palette.textSecondary,
    fontSize: FontSize.md,
    minWidth: 72,
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
    padding: Spacing[2],
    paddingBottom: Spacing[16],
  },
  pickerRow: {
    gap: Spacing[2],
    marginBottom: Spacing[2],
  },
  pickerCell: {
    flex: 1,
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
  checkmarkText: {
    color: '#000',
    fontSize: 11,
    fontWeight: FontWeight.bold,
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
