/**
 * Shared form used by both Add Item and Edit Item screens.
 * All fields from the clothing_items schema are represented.
 * Multi-selects (colors, materials, seasons, occasions, patterns) are handled inline.
 */

import * as ImagePicker from 'expo-image-picker';
import { Image } from 'expo-image';
import { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';

import { FontSize, FontWeight, Palette, Radius, Spacing } from '@/constants/tokens';
import { useAccent } from '@/context/AccentContext';
import { getDatabase } from '@/db';
import {
  getCategories,
  getColors,
  getMaterials,
  getOccasions,
  getPatterns,
  getSeasons,
  getSizeSystems,
  getSizeValues,
  getSubcategories,
} from '@/db/queries';
import {
  Category,
  Color,
  Material,
  Occasion,
  Pattern,
  Season,
  SizeSystem,
  SizeValue,
  Subcategory,
} from '@/db/types';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export type ItemFormValues = {
  name: string;
  brand: string;
  category_id: number | null;
  subcategory_id: number | null;
  size_system_id: number | null;
  size_value_id: number | null;
  waist: string;
  inseam: string;
  purchase_price: string;
  purchase_date: string;
  purchase_location: string;
  image_path: string | null;
  notes: string;
  status: 'Active' | 'Sold' | 'Donated' | 'Lost';
  wash_status: 'Clean' | 'Dirty';
  is_favorite: boolean;
  colorIds: number[];
  materialIds: number[];
  seasonIds: number[];
  occasionIds: number[];
  patternIds: number[];
};

export const EMPTY_FORM: ItemFormValues = {
  name: '',
  brand: '',
  category_id: null,
  subcategory_id: null,
  size_system_id: null,
  size_value_id: null,
  waist: '',
  inseam: '',
  purchase_price: '',
  purchase_date: '',
  purchase_location: '',
  image_path: null,
  notes: '',
  status: 'Active',
  wash_status: 'Clean',
  is_favorite: false,
  colorIds: [],
  materialIds: [],
  seasonIds: [],
  occasionIds: [],
  patternIds: [],
};

type Props = {
  initialValues?: ItemFormValues;
  onSubmit: (values: ItemFormValues) => Promise<void>;
  submitLabel: string;
  submitting: boolean;
};

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function ItemForm({ initialValues = EMPTY_FORM, onSubmit, submitLabel, submitting }: Props) {
  const { accent } = useAccent();
  const [values, setValues] = useState<ItemFormValues>(initialValues);
  const [nameError, setNameError] = useState<string | null>(null);

  // Lookup data
  const [categories, setCategories] = useState<Category[]>([]);
  const [subcategories, setSubcategories] = useState<Subcategory[]>([]);
  const [sizeSystems, setSizeSystems] = useState<SizeSystem[]>([]);
  const [sizeValues, setSizeValues] = useState<SizeValue[]>([]);
  const [seasons, setSeasons] = useState<Season[]>([]);
  const [occasions, setOccasions] = useState<Occasion[]>([]);
  const [colors, setColors] = useState<Color[]>([]);
  const [materials, setMaterials] = useState<Material[]>([]);
  const [patterns, setPatterns] = useState<Pattern[]>([]);

  // Load all lookup data once
  useEffect(() => {
    (async () => {
      const db = await getDatabase();
      const [cats, szs, seas, occs, cols, mats, pats] = await Promise.all([
        getCategories(db),
        getSizeSystems(db),
        getSeasons(db),
        getOccasions(db),
        getColors(db),
        getMaterials(db),
        getPatterns(db),
      ]);
      setCategories(cats);
      setSizeSystems(szs);
      setSeasons(seas);
      setOccasions(occs);
      setColors(cols);
      setMaterials(mats);
      setPatterns(pats);
    })();
  }, []);

  // Reload subcategories when category changes
  useEffect(() => {
    if (!values.category_id) { setSubcategories([]); return; }
    getDatabase().then((db) =>
      getSubcategories(db, values.category_id!).then(setSubcategories)
    );
  }, [values.category_id]);

  // Reload size values when size system changes
  useEffect(() => {
    if (!values.size_system_id) { setSizeValues([]); return; }
    getDatabase().then((db) =>
      getSizeValues(db, values.size_system_id!).then(setSizeValues)
    );
  }, [values.size_system_id]);

  const set = <K extends keyof ItemFormValues>(key: K, value: ItemFormValues[K]) => {
    setValues((v) => ({ ...v, [key]: value }));
  };

  const toggleMulti = (key: 'colorIds' | 'materialIds' | 'seasonIds' | 'occasionIds' | 'patternIds', id: number) => {
    setValues((v) => {
      const current = v[key];
      return {
        ...v,
        [key]: current.includes(id) ? current.filter((x) => x !== id) : [...current, id],
      };
    });
  };

  const pickImage = async () => {
    const { status } = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (status !== 'granted') {
      Alert.alert('Permission needed', 'Allow photo access to add clothing images.');
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      allowsEditing: true,
      aspect: [3, 4],
      quality: 0.85,
    });
    if (!result.canceled && result.assets[0]) {
      set('image_path', result.assets[0].uri);
    }
  };

  const handleSubmit = async () => {
    if (!values.name.trim()) {
      setNameError('Name is required');
      return;
    }
    setNameError(null);
    await onSubmit(values);
  };

  return (
    <ScrollView
      style={styles.scroll}
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
    >
      {/* Photo */}
      <FormSection label="Photo">
        <TouchableOpacity style={styles.photoButton} onPress={pickImage} activeOpacity={0.8}>
          {values.image_path ? (
            <Image
              source={{ uri: values.image_path }}
              style={styles.photoPreview}
              contentFit="cover"
            />
          ) : (
            <View style={styles.photoPlaceholder}>
              <Text style={styles.photoPlaceholderIcon}>📷</Text>
              <Text style={styles.photoPlaceholderText}>Add Photo</Text>
            </View>
          )}
        </TouchableOpacity>
        {values.image_path && (
          <TouchableOpacity onPress={() => set('image_path', null)} style={styles.removePhoto}>
            <Text style={styles.removePhotoText}>Remove photo</Text>
          </TouchableOpacity>
        )}
      </FormSection>

      {/* Name */}
      <FormSection label="Name *">
        <TextInput
          style={[styles.input, nameError ? styles.inputError : null]}
          value={values.name}
          onChangeText={(v) => { set('name', v); if (v.trim()) setNameError(null); }}
          placeholder="e.g. Navy Oxford Shirt"
          placeholderTextColor={Palette.textDisabled}
        />
        {nameError && <Text style={styles.errorText}>{nameError}</Text>}
      </FormSection>

      {/* Brand */}
      <FormSection label="Brand">
        <TextInput
          style={styles.input}
          value={values.brand}
          onChangeText={(v) => set('brand', v)}
          placeholder="e.g. Uniqlo"
          placeholderTextColor={Palette.textDisabled}
        />
      </FormSection>

      {/* Category */}
      <FormSection label="Category">
        <ChipSelector
          items={categories}
          selectedId={values.category_id}
          onSelect={(id) => {
            set('category_id', id);
            set('subcategory_id', null);
          }}
          accent={accent.primary}
        />
      </FormSection>

      {/* Subcategory */}
      {subcategories.length > 0 && (
        <FormSection label="Subcategory">
          <ChipSelector
            items={subcategories}
            selectedId={values.subcategory_id}
            onSelect={(id) => set('subcategory_id', id)}
            accent={accent.primary}
          />
        </FormSection>
      )}

      {/* Size system */}
      <FormSection label="Size System">
        <ChipSelector
          items={sizeSystems}
          selectedId={values.size_system_id}
          onSelect={(id) => {
            set('size_system_id', id);
            set('size_value_id', null);
          }}
          accent={accent.primary}
        />
      </FormSection>

      {/* Size value */}
      {sizeValues.length > 0 && (
        <FormSection label="Size">
          <ChipSelector
            items={sizeValues.map((sv) => ({ id: sv.id, name: sv.value }))}
            selectedId={values.size_value_id}
            onSelect={(id) => set('size_value_id', id)}
            accent={accent.primary}
          />
        </FormSection>
      )}

      {/* Waist / Inseam */}
      <View style={styles.row}>
        <View style={styles.halfField}>
          <FormSection label="Waist (in)">
            <TextInput
              style={styles.input}
              value={values.waist}
              onChangeText={(v) => set('waist', v)}
              placeholder="32.5"
              placeholderTextColor={Palette.textDisabled}
              keyboardType="decimal-pad"
            />
          </FormSection>
        </View>
        <View style={styles.halfField}>
          <FormSection label="Inseam (in)">
            <TextInput
              style={styles.input}
              value={values.inseam}
              onChangeText={(v) => set('inseam', v)}
              placeholder="30"
              placeholderTextColor={Palette.textDisabled}
              keyboardType="decimal-pad"
            />
          </FormSection>
        </View>
      </View>

      {/* Colors */}
      <FormSection label="Colors">
        <MultiChipSelector
          items={colors}
          selectedIds={values.colorIds}
          onToggle={(id) => toggleMulti('colorIds', id)}
          accent={accent.primary}
          renderDot={(color) => color.hex ? (
            <View style={[styles.colorDot, { backgroundColor: color.hex }]} />
          ) : null}
        />
      </FormSection>

      {/* Materials */}
      <FormSection label="Materials">
        <MultiChipSelector
          items={materials}
          selectedIds={values.materialIds}
          onToggle={(id) => toggleMulti('materialIds', id)}
          accent={accent.primary}
        />
      </FormSection>

      {/* Patterns */}
      <FormSection label="Pattern">
        <MultiChipSelector
          items={patterns}
          selectedIds={values.patternIds}
          onToggle={(id) => toggleMulti('patternIds', id)}
          accent={accent.primary}
        />
      </FormSection>

      {/* Seasons */}
      <FormSection label="Seasons">
        <MultiChipSelector
          items={seasons}
          selectedIds={values.seasonIds}
          onToggle={(id) => toggleMulti('seasonIds', id)}
          accent={accent.primary}
        />
      </FormSection>

      {/* Occasions */}
      <FormSection label="Occasions">
        <MultiChipSelector
          items={occasions}
          selectedIds={values.occasionIds}
          onToggle={(id) => toggleMulti('occasionIds', id)}
          accent={accent.primary}
        />
      </FormSection>

      {/* Purchase info */}
      <FormSection label="Purchase Price">
        <TextInput
          style={styles.input}
          value={values.purchase_price}
          onChangeText={(v) => set('purchase_price', v)}
          placeholder="0.00"
          placeholderTextColor={Palette.textDisabled}
          keyboardType="decimal-pad"
        />
      </FormSection>

      <FormSection label="Purchase Date">
        <TextInput
          style={styles.input}
          value={values.purchase_date}
          onChangeText={(v) => set('purchase_date', v)}
          placeholder="YYYY-MM-DD"
          placeholderTextColor={Palette.textDisabled}
        />
      </FormSection>

      <FormSection label="Purchase Location">
        <TextInput
          style={styles.input}
          value={values.purchase_location}
          onChangeText={(v) => set('purchase_location', v)}
          placeholder="e.g. Uniqlo online"
          placeholderTextColor={Palette.textDisabled}
        />
      </FormSection>

      {/* Status */}
      <FormSection label="Status">
        <ChipSelector
          items={[
            { id: 'Active', name: 'Active' },
            { id: 'Sold', name: 'Sold' },
            { id: 'Donated', name: 'Donated' },
            { id: 'Lost', name: 'Lost' },
          ]}
          selectedId={values.status}
          onSelect={(id) => set('status', id as ItemFormValues['status'])}
          accent={accent.primary}
        />
      </FormSection>

      {/* Wash status */}
      <FormSection label="Wash Status">
        <ChipSelector
          items={[
            { id: 'Clean', name: 'Clean' },
            { id: 'Dirty', name: 'Dirty' },
          ]}
          selectedId={values.wash_status}
          onSelect={(id) => set('wash_status', id as ItemFormValues['wash_status'])}
          accent={accent.primary}
        />
      </FormSection>

      {/* Favorite toggle */}
      <FormSection label="Favorite">
        <TouchableOpacity
          style={[styles.toggleButton, values.is_favorite && { borderColor: accent.primary }]}
          onPress={() => set('is_favorite', !values.is_favorite)}
          activeOpacity={0.8}
        >
          <Text style={[styles.toggleText, values.is_favorite && { color: accent.primary }]}>
            {values.is_favorite ? '♥  Favorited' : '♡  Add to favorites'}
          </Text>
        </TouchableOpacity>
      </FormSection>

      {/* Notes */}
      <FormSection label="Notes">
        <TextInput
          style={[styles.input, styles.textArea]}
          value={values.notes}
          onChangeText={(v) => set('notes', v)}
          placeholder="Any notes about this item..."
          placeholderTextColor={Palette.textDisabled}
          multiline
          numberOfLines={4}
          textAlignVertical="top"
        />
      </FormSection>

      {/* Submit */}
      <TouchableOpacity
        style={[styles.submitButton, { backgroundColor: accent.primary }]}
        onPress={handleSubmit}
        disabled={submitting}
        activeOpacity={0.85}
      >
        {submitting ? (
          <ActivityIndicator color="#000" />
        ) : (
          <Text style={styles.submitText}>{submitLabel}</Text>
        )}
      </TouchableOpacity>

      <View style={{ height: Spacing[8] }} />
    </ScrollView>
  );
}

// ---------------------------------------------------------------------------
// Sub-components
// ---------------------------------------------------------------------------

function FormSection({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <View style={styles.section}>
      <Text style={styles.label}>{label}</Text>
      {children}
    </View>
  );
}

type ChipItem = { id: string | number; name: string };

function ChipSelector({
  items,
  selectedId,
  onSelect,
  accent,
}: {
  items: ChipItem[];
  selectedId: string | number | null;
  onSelect: (id: string | number) => void;
  accent: string;
}) {
  return (
    <View style={styles.chips}>
      {items.map((item) => {
        const selected = item.id === selectedId;
        return (
          <Pressable
            key={String(item.id)}
            style={[styles.chip, selected && { backgroundColor: accent, borderColor: accent }]}
            onPress={() => onSelect(selected ? null as any : item.id)}
          >
            <Text style={[styles.chipText, selected && styles.chipTextSelected]}>
              {item.name}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

function MultiChipSelector<T extends { id: number; name: string }>({
  items,
  selectedIds,
  onToggle,
  accent,
  renderDot,
}: {
  items: T[];
  selectedIds: number[];
  onToggle: (id: number) => void;
  accent: string;
  renderDot?: (item: T) => React.ReactNode;
}) {
  return (
    <View style={styles.chips}>
      {items.map((item) => {
        const selected = selectedIds.includes(item.id);
        return (
          <Pressable
            key={item.id}
            style={[styles.chip, selected && { backgroundColor: accent, borderColor: accent }]}
            onPress={() => onToggle(item.id)}
          >
            {renderDot?.(item)}
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
  scroll: {
    flex: 1,
    backgroundColor: Palette.surface0,
  },
  content: {
    padding: Spacing[4],
  },
  section: {
    marginBottom: Spacing[5],
  },
  label: {
    color: Palette.textSecondary,
    fontSize: FontSize.sm,
    fontWeight: FontWeight.medium,
    marginBottom: Spacing[2],
    textTransform: 'uppercase',
    letterSpacing: 0.6,
  },
  input: {
    backgroundColor: Palette.surface2,
    borderRadius: Radius.md,
    paddingHorizontal: Spacing[3],
    paddingVertical: Spacing[3],
    color: Palette.textPrimary,
    fontSize: FontSize.md,
    borderWidth: 1,
    borderColor: Palette.border,
  },
  inputError: {
    borderColor: Palette.error,
  },
  errorText: {
    color: Palette.error,
    fontSize: FontSize.sm,
    marginTop: Spacing[1],
  },
  textArea: {
    minHeight: 100,
  },
  row: {
    flexDirection: 'row',
    gap: Spacing[3],
  },
  halfField: {
    flex: 1,
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
  photoButton: {
    width: '100%',
    aspectRatio: 3 / 4,
    borderRadius: Radius.lg,
    overflow: 'hidden',
    backgroundColor: Palette.surface2,
    borderWidth: 1,
    borderColor: Palette.border,
    maxHeight: 300,
  },
  photoPreview: {
    width: '100%',
    height: '100%',
  },
  photoPlaceholder: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing[2],
  },
  photoPlaceholderIcon: {
    fontSize: 36,
  },
  photoPlaceholderText: {
    color: Palette.textSecondary,
    fontSize: FontSize.md,
  },
  removePhoto: {
    marginTop: Spacing[2],
    alignSelf: 'center',
  },
  removePhotoText: {
    color: Palette.error,
    fontSize: FontSize.sm,
  },
  toggleButton: {
    paddingHorizontal: Spacing[4],
    paddingVertical: Spacing[3],
    borderRadius: Radius.md,
    borderWidth: 1,
    borderColor: Palette.border,
    backgroundColor: Palette.surface2,
    alignSelf: 'flex-start',
  },
  toggleText: {
    color: Palette.textSecondary,
    fontSize: FontSize.md,
  },
  submitButton: {
    paddingVertical: Spacing[4],
    borderRadius: Radius.md,
    alignItems: 'center',
    marginTop: Spacing[4],
  },
  submitText: {
    color: '#000',
    fontSize: FontSize.lg,
    fontWeight: FontWeight.semibold,
  },
});
