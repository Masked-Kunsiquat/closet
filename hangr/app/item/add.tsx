import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Pressable, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { EMPTY_FORM, ItemForm, ItemFormValues } from '@/components/clothing/ItemForm';
import { FontSize, FontWeight, Palette, Spacing } from '@/constants/tokens';
import { getDatabase } from '@/db';
import {
  insertClothingItem,
  setClothingItemColors,
  setClothingItemMaterials,
  setClothingItemOccasions,
  setClothingItemPatterns,
  setClothingItemSeasons,
} from '@/db/queries';

/**
 * Renders the "Add Item" screen with a form for creating a clothing item.
 *
 * Submitting the form persists the item and its associated attributes to the local database; on success it navigates to the newly created item's detail screen, and on failure it shows an inline error banner.
 *
 * @returns The React element for the Add Item screen.
 */
export default function AddItemScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [submitting, setSubmitting] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const handleSubmit = async (values: ItemFormValues) => {
    setSubmitting(true);
    try {
      const db = await getDatabase();

      const itemId = await insertClothingItem(db, {
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

      router.replace(`/item/${itemId}`);
    } catch (e) {
      console.error('[add item]', e);
      setSaveError('Failed to save item. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <View style={[styles.container, { paddingTop: insets.top }]}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} hitSlop={12}>
          <Text style={styles.cancel}>Cancel</Text>
        </TouchableOpacity>
        <Text style={styles.title}>Add Item</Text>
        <View style={{ width: 56 }} />
      </View>

      {saveError && (
        <Pressable style={styles.errorBanner} onPress={() => setSaveError(null)}>
          <Text style={styles.errorText}>{saveError}</Text>
          <Text style={styles.errorDismiss}>✕</Text>
        </Pressable>
      )}

      <ItemForm
        initialValues={EMPTY_FORM}
        onSubmit={handleSubmit}
        submitLabel="Add to Closet"
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