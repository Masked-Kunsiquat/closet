import { useLocalSearchParams, useRouter } from 'expo-router';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { EMPTY_FORM, ItemForm, ItemFormValues } from '@/components/clothing/ItemForm';
import { FontSize, FontWeight, Palette, Spacing } from '@/constants/tokens';
import { getDatabase } from '@/db';
import {
  getClothingItemById,
  getClothingItemColorIds,
  getClothingItemMaterialIds,
  getClothingItemOccasionIds,
  getClothingItemPatternIds,
  getClothingItemSeasonIds,
  getSizeValues,
  setClothingItemColors,
  setClothingItemMaterials,
  setClothingItemOccasions,
  setClothingItemPatterns,
  setClothingItemSeasons,
  updateClothingItem,
} from '@/db/queries';

/**
 * Screen for editing an existing clothing item, including loading its data, resolving related attributes, and saving updates.
 *
 * @returns A React element that renders the Edit Item screen with loading and error states, a pre-filled ItemForm, and handlers to persist changes.
 */
export default function EditItemScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const itemId = Number(id);
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [initialValues, setInitialValues] = useState<ItemFormValues | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  // Load existing item data and resolve size_system_id from size_value_id
  useEffect(() => {
    (async () => {
      try {
        const db = await getDatabase();
        const [item, colorIds, materialIds, seasonIds, occasionIds, patternIds] = await Promise.all([
          getClothingItemById(db, itemId),
          getClothingItemColorIds(db, itemId),
          getClothingItemMaterialIds(db, itemId),
          getClothingItemSeasonIds(db, itemId),
          getClothingItemOccasionIds(db, itemId),
          getClothingItemPatternIds(db, itemId),
        ]);

        if (!item) {
          setLoadError('Item not found.');
          return;
        }

        // Resolve which size system this value belongs to
        let size_system_id: number | null = null;
        if (item.size_value_id) {
          const allSystems = await db.getAllAsync<{ id: number }>(`SELECT id FROM size_systems`);
          for (const sys of allSystems) {
            const vals = await getSizeValues(db, sys.id);
            if (vals.some((v) => v.id === item.size_value_id)) {
              size_system_id = sys.id;
              break;
            }
          }
        }

        setInitialValues({
          ...EMPTY_FORM,
          name: item.name,
          brand: item.brand ?? '',
          category_id: item.category_id,
          subcategory_id: item.subcategory_id,
          size_system_id,
          size_value_id: item.size_value_id,
          waist: item.waist != null ? String(item.waist) : '',
          inseam: item.inseam != null ? String(item.inseam) : '',
          purchase_price: item.purchase_price != null ? String(item.purchase_price) : '',
          purchase_date: item.purchase_date ?? '',
          purchase_location: item.purchase_location ?? '',
          image_path: item.image_path,
          notes: item.notes ?? '',
          status: item.status,
          wash_status: item.wash_status,
          is_favorite: item.is_favorite === 1,
          colorIds,
          materialIds,
          seasonIds,
          occasionIds,
          patternIds,
        });
      } catch (e) {
        setLoadError(String(e));
      }
    })();
  }, [itemId, router]);

  const handleSubmit = async (values: ItemFormValues) => {
    setSubmitting(true);
    try {
      const db = await getDatabase();

      await updateClothingItem(db, itemId, {
        name: values.name.trim(),
        brand: values.brand.trim() || null,
        category_id: values.category_id,
        subcategory_id: values.subcategory_id,
        size_value_id: values.size_value_id,
        waist: values.waist ? parseFloat(values.waist) : null,
        inseam: values.inseam ? parseFloat(values.inseam) : null,
        purchase_price: values.purchase_price ? parseFloat(values.purchase_price) : null,
        purchase_date: values.purchase_date.trim() || null,
        purchase_location: values.purchase_location.trim() || null,
        image_path: values.image_path,
        notes: values.notes.trim() || null,
        status: values.status,
        wash_status: values.wash_status,
        is_favorite: values.is_favorite ? 1 : 0,
      });

      await setClothingItemColors(db, itemId, values.colorIds);
      await setClothingItemMaterials(db, itemId, values.materialIds);
      await setClothingItemSeasons(db, itemId, values.seasonIds);
      await setClothingItemOccasions(db, itemId, values.occasionIds);
      await setClothingItemPatterns(db, itemId, values.patternIds);

      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      router.back();
    } catch (e) {
      console.error('[edit item]', e);
      setSaveError('Failed to save changes. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  if (loadError) {
    return (
      <View style={[styles.container, styles.centered]}>
        <Text style={styles.muted}>Failed to load item.</Text>
        <TouchableOpacity onPress={() => router.back()} hitSlop={12} style={{ marginTop: Spacing[4] }}>
          <Text style={[styles.cancel, { color: Palette.textPrimary }]}>Go Back</Text>
        </TouchableOpacity>
      </View>
    );
  }

  if (!initialValues) {
    return (
      <View style={[styles.container, styles.centered]}>
        <Text style={styles.muted}>Loading…</Text>
      </View>
    );
  }

  return (
    <View style={[styles.container, { paddingTop: insets.top }]}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} hitSlop={12}>
          <Text style={styles.cancel}>Cancel</Text>
        </TouchableOpacity>
        <Text style={styles.title}>Edit Item</Text>
        <View style={{ width: 56 }} />
      </View>

      {saveError && (
        <Pressable style={styles.errorBanner} onPress={() => setSaveError(null)}>
          <Text style={styles.errorText}>{saveError}</Text>
          <Text style={styles.errorDismiss}>✕</Text>
        </Pressable>
      )}

      <ItemForm
        initialValues={initialValues}
        onSubmit={handleSubmit}
        submitLabel="Save Changes"
        submitting={submitting}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Palette.surface0,
  },
  centered: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  muted: {
    color: Palette.textSecondary,
    fontSize: FontSize.md,
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
  title: {
    color: Palette.textPrimary,
    fontSize: FontSize.lg,
    fontWeight: FontWeight.semibold,
  },
  cancel: {
    color: Palette.textSecondary,
    fontSize: FontSize.md,
  },
  errorBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: Palette.error,
    paddingHorizontal: Spacing[4],
    paddingVertical: Spacing[3],
  },
  errorText: {
    color: Palette.white,
    fontSize: FontSize.sm,
    flex: 1,
  },
  errorDismiss: {
    color: Palette.white,
    fontSize: FontSize.sm,
    marginLeft: Spacing[3],
  },
});