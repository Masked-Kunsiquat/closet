import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Alert, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
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

export default function AddItemScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [submitting, setSubmitting] = useState(false);

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

      await Promise.all([
        setClothingItemColors(db, itemId, values.colorIds),
        setClothingItemMaterials(db, itemId, values.materialIds),
        setClothingItemSeasons(db, itemId, values.seasonIds),
        setClothingItemOccasions(db, itemId, values.occasionIds),
        setClothingItemPatterns(db, itemId, values.patternIds),
      ]);

      router.replace(`/item/${itemId}`);
    } catch (e) {
      Alert.alert('Error', 'Failed to save item. Please try again.');
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
});
