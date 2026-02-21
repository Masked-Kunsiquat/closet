/**
 * Shared form used by both Add Item and Edit Item screens.
 * All fields from the clothing_items schema are represented.
 *
 * Layout:
 *   - Always visible: Photo, Name, Brand
 *   - Collapsible: Details, Attributes, Context, Purchase Info
 *   - Always visible at bottom: Notes, Status, Wash Status, Favorite, Submit
 *
 * Chip-heavy fields (Colors, Materials, Pattern, Seasons, Occasions) open
 * a PickerSheet bottom sheet instead of rendering inline.
 */

import * as ImagePicker from 'expo-image-picker';
import { Image } from 'expo-image';
import { ReactNode, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';

import { FontSize, FontWeight, Palette, Radius, Spacing } from '@/constants/tokens';
import { PhosphorIcon } from '@/components/PhosphorIcon';
import { useAccent } from '@/context/AccentContext';
import { contrastingTextColor } from '@/utils/color';
import { getDatabase } from '@/db';
import {
  getCategories,
  getColors,
  getDistinctBrands,
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
import { PickerSheet } from './PickerSheet';

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
// Section summary helpers
// ---------------------------------------------------------------------------

function detailsSummary(
  values: ItemFormValues,
  categories: Category[],
  subcategories: Subcategory[],
  sizeValues: SizeValue[],
): string {
  const parts: string[] = [];
  const cat = categories.find((c) => c.id === values.category_id);
  if (cat) parts.push(cat.name);
  const sub = subcategories.find((s) => s.id === values.subcategory_id);
  if (sub) parts.push(sub.name);
  const sv = sizeValues.find((s) => s.id === values.size_value_id);
  if (sv) parts.push(sv.value);
  if (values.waist) parts.push(`W${values.waist}`);
  if (values.inseam) parts.push(`L${values.inseam}`);
  return parts.join(' · ');
}

function attributesSummary(
  values: ItemFormValues,
  materials: Material[],
  patterns: Pattern[],
): string {
  const parts: string[] = [];
  if (values.colorIds.length > 0) {
    parts.push(values.colorIds.length === 1 ? '1 color' : `${values.colorIds.length} colors`);
  }
  if (values.materialIds.length === 1) {
    const m = materials.find((x) => x.id === values.materialIds[0]);
    if (m) parts.push(m.name);
  } else if (values.materialIds.length > 1) {
    parts.push(`${values.materialIds.length} materials`);
  }
  if (values.patternIds.length > 0) {
    const p = patterns.find((x) => x.id === values.patternIds[0]);
    if (p) parts.push(p.name);
  }
  return parts.join(' · ');
}

function contextSummary(
  values: ItemFormValues,
  seasons: Season[],
  occasions: Occasion[],
): string {
  const parts: string[] = [];
  if (values.seasonIds.length === 1) {
    const s = seasons.find((x) => x.id === values.seasonIds[0]);
    if (s) parts.push(s.name);
  } else if (values.seasonIds.length > 1) {
    parts.push(`${values.seasonIds.length} seasons`);
  }
  if (values.occasionIds.length === 1) {
    const o = occasions.find((x) => x.id === values.occasionIds[0]);
    if (o) parts.push(o.name);
  } else if (values.occasionIds.length > 1) {
    parts.push(`${values.occasionIds.length} occasions`);
  }
  return parts.join(' · ');
}

function purchaseSummary(values: ItemFormValues): string {
  const parts: string[] = [];
  if (values.purchase_price) parts.push(`$${values.purchase_price}`);
  if (values.purchase_date) parts.push(values.purchase_date);
  if (values.purchase_location) parts.push(values.purchase_location);
  return parts.join(' · ');
}

// ---------------------------------------------------------------------------
// Category → relevant size systems mapping
// ---------------------------------------------------------------------------

const CATEGORY_SIZE_SYSTEMS: Record<string, string[]> = {
  'Tops':                   ['Letter', "Women's Numeric", 'One Size'],
  'Bottoms':                ['Letter', "Women's Numeric", 'One Size'],
  'Outerwear':              ['Letter', "Women's Numeric", 'One Size'],
  'Dresses & Jumpsuits':    ['Letter', "Women's Numeric", 'One Size'],
  'Footwear':               ["Shoes (US Men's)", "Shoes (US Women's)", 'Shoes (EU)', 'Shoes (UK)'],
  'Accessories':            ['One Size', 'Letter'],
  'Bags':                   ['One Size'],
  'Activewear':             ['Letter', "Women's Numeric", 'One Size'],
  'Underwear & Intimates':  ['Bra', 'Letter', 'One Size'],
  'Swimwear':               ['Letter', "Women's Numeric", 'One Size'],
};

// ---------------------------------------------------------------------------
// Sheet open state
// ---------------------------------------------------------------------------

type OpenSheet = 'colors' | 'materials' | 'patterns' | 'seasons' | 'occasions' | 'category' | 'subcategory' | null;

// ---------------------------------------------------------------------------
// Component
/**
 * Render a reusable form for creating or editing a clothing item.
 *
 * Fields are grouped into four collapsible sections (Details, Attributes,
 * Context, Purchase Info). Chip-heavy multi-selects open a PickerSheet
 * bottom sheet. Photo, Name, Brand, Notes, Status, Wash Status, Favorite,
 * and Submit are always visible.
 *
 * @param initialValues - Optional initial field values; defaults to `EMPTY_FORM`
 * @param onSubmit - Callback invoked with the current ItemFormValues when the form is submitted and valid
 * @param submitLabel - Text displayed on the submit button
 * @param submitting - When true, disables the submit button and shows a loading indicator
 */
// ---------------------------------------------------------------------------

export function ItemForm({ initialValues = EMPTY_FORM, onSubmit, submitLabel, submitting }: Props) {
  const { accent } = useAccent();
  const [values, setValues] = useState<ItemFormValues>(initialValues);
  const [nameError, setNameError] = useState<string | null>(null);
  const [openSheet, setOpenSheet] = useState<OpenSheet>(null);

  // Section open/close state
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [attributesOpen, setAttributesOpen] = useState(false);
  const [contextOpen, setContextOpen] = useState(false);
  const [purchaseOpen, setPurchaseOpen] = useState(false);

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
  const [allBrands, setAllBrands] = useState<string[]>([]);

  // Load all lookup data once
  useEffect(() => {
    (async () => {
      try {
        const db = await getDatabase();
        const [cats, szs, seas, occs, cols, mats, pats, brands] = await Promise.all([
          getCategories(db),
          getSizeSystems(db),
          getSeasons(db),
          getOccasions(db),
          getColors(db),
          getMaterials(db),
          getPatterns(db),
          getDistinctBrands(db),
        ]);
        setCategories(cats);
        setSizeSystems(szs);
        setSeasons(seas);
        setOccasions(occs);
        setColors(cols);
        setMaterials(mats);
        setPatterns(pats);
        setAllBrands(brands);
      } catch (e) {
        console.error('[ItemForm] failed to load lookup data', e);
      }
    })();
  }, []);

  // Reload subcategories when category changes (alphabetized)
  useEffect(() => {
    if (!values.category_id) { setSubcategories([]); return; }
    getDatabase()
      .then((db) =>
        getSubcategories(db, values.category_id!).then((subs) =>
          setSubcategories([...subs].sort((a, b) => a.name.localeCompare(b.name)))
        )
      )
      .catch((e) => { console.error('[ItemForm] subcategories', e); setSubcategories([]); });
  }, [values.category_id]);

  // Derived: selected category
  const selectedCategory = categories.find((c) => c.id === values.category_id) ?? null;
  const selectedCategoryName = selectedCategory?.name ?? null;
  const selectedCategoryIcon = selectedCategory?.icon ?? null;

  // Derived: filtered size systems based on selected category
  const filteredSizeSystems = selectedCategoryName && CATEGORY_SIZE_SYSTEMS[selectedCategoryName]
    ? sizeSystems.filter((s) => CATEGORY_SIZE_SYSTEMS[selectedCategoryName].includes(s.name))
    : sizeSystems;

  // Derived: show waist/inseam only for Bottoms
  const showWaistInseam = selectedCategoryName === 'Bottoms';

  // Derived: brand autocomplete suggestions
  const brandSuggestions = values.brand.trim().length > 0
    ? allBrands.filter(
        (b) =>
          b.toLowerCase().includes(values.brand.toLowerCase()) &&
          b.toLowerCase() !== values.brand.toLowerCase()
      )
    : [];

  // Reload size values when size system changes
  useEffect(() => {
    if (!values.size_system_id) { setSizeValues([]); return; }
    getDatabase()
      .then((db) => getSizeValues(db, values.size_system_id!).then(setSizeValues))
      .catch((e) => { console.error('[ItemForm] size values', e); setSizeValues([]); });
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

  // Called when user picks a category; clears stale sizing data
  const handleCategoryChange = (id: number | null) => {
    const newCatName = id !== null ? categories.find((c) => c.id === id)?.name ?? null : null;
    const allowedSystems = newCatName ? (CATEGORY_SIZE_SYSTEMS[newCatName] ?? []) : null;
    const currentSystemName = sizeSystems.find((s) => s.id === values.size_system_id)?.name ?? null;
    const systemStillValid = allowedSystems === null || (currentSystemName !== null && allowedSystems.includes(currentSystemName));
    setValues((v) => ({
      ...v,
      category_id: id,
      subcategory_id: null,
      size_system_id: systemStillValid ? v.size_system_id : null,
      size_value_id: systemStillValid ? v.size_value_id : null,
      waist: newCatName === 'Bottoms' ? v.waist : '',
      inseam: newCatName === 'Bottoms' ? v.inseam : '',
    }));
  };

  // Computed summaries
  const detailsSummaryText = detailsSummary(values, categories, subcategories, sizeValues);
  const attributesSummaryText = attributesSummary(values, materials, patterns);
  const contextSummaryText = contextSummary(values, seasons, occasions);
  const purchaseSummaryText = purchaseSummary(values);

  return (
    <>
      <ScrollView
        style={styles.scroll}
        contentContainerStyle={styles.content}
        keyboardShouldPersistTaps="handled"
      >
        {/* ── Photo ── */}
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

        <View style={styles.divider} />

        {/* ── Name ── */}
        <View style={styles.field}>
          <Text style={styles.fieldLabel}>Name *</Text>
          <TextInput
            style={[styles.input, nameError ? styles.inputError : null]}
            value={values.name}
            onChangeText={(v) => { set('name', v); if (v.trim()) setNameError(null); }}
            placeholder="e.g. Navy Oxford Shirt"
            placeholderTextColor={Palette.textDisabled}
          />
          {nameError && <Text style={styles.errorText}>{nameError}</Text>}
        </View>

        {/* ── Brand ── */}
        <View style={styles.field}>
          <Text style={styles.fieldLabel}>Brand</Text>
          <TextInput
            style={styles.input}
            value={values.brand}
            onChangeText={(v) => set('brand', v)}
            placeholder="e.g. Uniqlo"
            placeholderTextColor={Palette.textDisabled}
          />
          {brandSuggestions.length > 0 && (
            <ScrollView
              horizontal
              showsHorizontalScrollIndicator={false}
              style={styles.suggestionsRow}
              keyboardShouldPersistTaps="handled"
            >
              {brandSuggestions.map((b) => (
                <Pressable
                  key={b}
                  style={styles.suggestionChip}
                  onPress={() => set('brand', b)}
                >
                  <Text style={styles.suggestionChipText}>{b}</Text>
                </Pressable>
              ))}
            </ScrollView>
          )}
        </View>

        {/* ── Details (collapsible) ── */}
        <FormCollapsible
          title="Details"
          summary={detailsSummaryText}
          open={detailsOpen}
          onToggle={() => setDetailsOpen((x) => !x)}
        >
          <PickerTrigger
            label="Category"
            value={selectedCategoryName ?? 'None'}
            icon={selectedCategoryIcon}
            onPress={() => setOpenSheet('category')}
          />

          {subcategories.length > 0 && (
            <PickerTrigger
              label="Subcategory"
              value={subcategories.find((s) => s.id === values.subcategory_id)?.name ?? 'None'}
              onPress={() => setOpenSheet('subcategory')}
            />
          )}

          <View style={styles.field}>
            <Text style={styles.fieldLabel}>Size System</Text>
            <ChipSelector
              items={filteredSizeSystems}
              selectedId={values.size_system_id}
              onSelect={(id) => {
                set('size_system_id', id === null ? null : Number(id));
                set('size_value_id', null);
              }}
              accent={accent.primary}
            />
          </View>

          {sizeValues.length > 0 && (
            <View style={styles.field}>
              <Text style={styles.fieldLabel}>Size</Text>
              <ChipSelector
                items={sizeValues.map((sv) => ({ id: sv.id, name: sv.value }))}
                selectedId={values.size_value_id}
                onSelect={(id) => set('size_value_id', id === null ? null : Number(id))}
                accent={accent.primary}
              />
            </View>
          )}

          {showWaistInseam && (
            <View style={[styles.row, styles.field]}>
              <View style={styles.halfField}>
                <Text style={styles.fieldLabel}>Waist (in)</Text>
                <TextInput
                  style={styles.input}
                  value={values.waist}
                  onChangeText={(v) => set('waist', v)}
                  placeholder="32.5"
                  placeholderTextColor={Palette.textDisabled}
                  keyboardType="decimal-pad"
                />
              </View>
              <View style={styles.halfField}>
                <Text style={styles.fieldLabel}>Inseam (in)</Text>
                <TextInput
                  style={styles.input}
                  value={values.inseam}
                  onChangeText={(v) => set('inseam', v)}
                  placeholder="30"
                  placeholderTextColor={Palette.textDisabled}
                  keyboardType="decimal-pad"
                />
              </View>
            </View>
          )}
        </FormCollapsible>

        {/* ── Attributes (collapsible) ── */}
        <FormCollapsible
          title="Attributes"
          summary={attributesSummaryText}
          open={attributesOpen}
          onToggle={() => setAttributesOpen((x) => !x)}
        >
          <PickerTrigger
            label="Colors"
            count={values.colorIds.length}
            onPress={() => setOpenSheet('colors')}
          />
          <PickerTrigger
            label="Materials"
            count={values.materialIds.length}
            onPress={() => setOpenSheet('materials')}
          />
          <PickerTrigger
            label="Pattern"
            count={values.patternIds.length}
            onPress={() => setOpenSheet('patterns')}
          />
        </FormCollapsible>

        {/* ── Context (collapsible) ── */}
        <FormCollapsible
          title="Context"
          summary={contextSummaryText}
          open={contextOpen}
          onToggle={() => setContextOpen((x) => !x)}
        >
          <PickerTrigger
            label="Seasons"
            count={values.seasonIds.length}
            onPress={() => setOpenSheet('seasons')}
          />
          <PickerTrigger
            label="Occasions"
            count={values.occasionIds.length}
            onPress={() => setOpenSheet('occasions')}
          />
        </FormCollapsible>

        {/* ── Purchase Info (collapsible) ── */}
        <FormCollapsible
          title="Purchase Info"
          summary={purchaseSummaryText}
          open={purchaseOpen}
          onToggle={() => setPurchaseOpen((x) => !x)}
        >
          <View style={styles.field}>
            <Text style={styles.fieldLabel}>Price</Text>
            <TextInput
              style={styles.input}
              value={values.purchase_price}
              onChangeText={(v) => set('purchase_price', v)}
              placeholder="0.00"
              placeholderTextColor={Palette.textDisabled}
              keyboardType="decimal-pad"
            />
          </View>
          <View style={styles.field}>
            <Text style={styles.fieldLabel}>Date</Text>
            <TextInput
              style={styles.input}
              value={values.purchase_date}
              onChangeText={(v) => set('purchase_date', v)}
              placeholder="YYYY-MM-DD"
              placeholderTextColor={Palette.textDisabled}
            />
          </View>
          <View style={styles.field}>
            <Text style={styles.fieldLabel}>Location</Text>
            <TextInput
              style={styles.input}
              value={values.purchase_location}
              onChangeText={(v) => set('purchase_location', v)}
              placeholder="e.g. Uniqlo online"
              placeholderTextColor={Palette.textDisabled}
            />
          </View>
        </FormCollapsible>

        <View style={styles.divider} />

        {/* ── Notes ── */}
        <View style={styles.field}>
          <Text style={styles.fieldLabel}>Notes</Text>
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
        </View>

        <View style={styles.divider} />

        {/* ── Status ── */}
        <View style={styles.field}>
          <Text style={styles.fieldLabel}>Status</Text>
          <ChipSelector
            items={[
              { id: 'Active', name: 'Active' },
              { id: 'Sold', name: 'Sold' },
              { id: 'Donated', name: 'Donated' },
              { id: 'Lost', name: 'Lost' },
            ]}
            selectedId={values.status}
            onSelect={(id) => { if (id !== null && id !== values.status) set('status', id as ItemFormValues['status']); }}
            accent={accent.primary}
          />
        </View>

        {/* ── Wash Status ── */}
        <View style={styles.field}>
          <Text style={styles.fieldLabel}>Wash Status</Text>
          <ChipSelector
            items={[
              { id: 'Clean', name: 'Clean' },
              { id: 'Dirty', name: 'Dirty' },
            ]}
            selectedId={values.wash_status}
            onSelect={(id) => { if (id !== null && id !== values.wash_status) set('wash_status', id as ItemFormValues['wash_status']); }}
            accent={accent.primary}
          />
        </View>

        {/* ── Favorite ── */}
        <View style={styles.field}>
          <TouchableOpacity
            style={[styles.toggleButton, values.is_favorite && { borderColor: accent.primary }]}
            onPress={() => set('is_favorite', !values.is_favorite)}
            activeOpacity={0.8}
          >
            <Text style={[styles.toggleText, values.is_favorite && { color: accent.primary }]}>
              {values.is_favorite ? '♥  Favorited' : '♡  Add to favorites'}
            </Text>
          </TouchableOpacity>
        </View>

        {/* ── Submit ── */}
        <TouchableOpacity
          style={[styles.submitButton, { backgroundColor: accent.primary }]}
          onPress={handleSubmit}
          disabled={submitting}
          activeOpacity={0.85}
        >
          {submitting ? (
            <ActivityIndicator color={contrastingTextColor(accent.primary)} />
          ) : (
            <Text style={[styles.submitText, { color: contrastingTextColor(accent.primary) }]}>{submitLabel}</Text>
          )}
        </TouchableOpacity>

        <View style={{ height: Spacing[8] }} />
      </ScrollView>

      {/* ── Category sheet ── */}
      <CategorySheet
        visible={openSheet === 'category'}
        categories={categories}
        selectedId={values.category_id}
        onSelect={(id) => { handleCategoryChange(id); setOpenSheet(null); }}
        onClose={() => setOpenSheet(null)}
        accentPrimary={accent.primary}
      />

      {/* ── Subcategory sheet ── */}
      <SubcategorySheet
        visible={openSheet === 'subcategory'}
        subcategories={subcategories}
        selectedId={values.subcategory_id}
        onSelect={(id) => { set('subcategory_id', id); setOpenSheet(null); }}
        onClose={() => setOpenSheet(null)}
        accentPrimary={accent.primary}
      />

      {/* ── Picker sheets ── */}
      <PickerSheet
        visible={openSheet === 'colors'}
        onClose={() => setOpenSheet(null)}
        title="Colors"
        items={colors}
        selectedIds={values.colorIds}
        onToggle={(id) => toggleMulti('colorIds', id)}
        renderDot={(color) => color.hex ? (
          <View style={[styles.colorDot, { backgroundColor: color.hex }]} />
        ) : null}
      />
      <PickerSheet
        visible={openSheet === 'materials'}
        onClose={() => setOpenSheet(null)}
        title="Materials"
        items={materials}
        selectedIds={values.materialIds}
        onToggle={(id) => toggleMulti('materialIds', id)}
      />
      <PickerSheet
        visible={openSheet === 'patterns'}
        onClose={() => setOpenSheet(null)}
        title="Pattern"
        items={patterns}
        selectedIds={values.patternIds}
        onToggle={(id) => toggleMulti('patternIds', id)}
      />
      <PickerSheet
        visible={openSheet === 'seasons'}
        onClose={() => setOpenSheet(null)}
        title="Seasons"
        items={seasons}
        selectedIds={values.seasonIds}
        onToggle={(id) => toggleMulti('seasonIds', id)}
      />
      <PickerSheet
        visible={openSheet === 'occasions'}
        onClose={() => setOpenSheet(null)}
        title="Occasions"
        items={occasions}
        selectedIds={values.occasionIds}
        onToggle={(id) => toggleMulti('occasionIds', id)}
      />
    </>
  );
}

// ---------------------------------------------------------------------------
// Sub-components
// ---------------------------------------------------------------------------

/**
 * Collapsible section header with a summary line when collapsed.
 */
function FormCollapsible({
  title,
  summary,
  open,
  onToggle,
  children,
}: {
  title: string;
  summary: string;
  open: boolean;
  onToggle: () => void;
  children: ReactNode;
}) {
  return (
    <View style={styles.collapsibleSection}>
      <Pressable
        style={styles.collapsibleHeader}
        onPress={onToggle}
        accessibilityRole="button"
        accessibilityState={{ expanded: open }}
        accessibilityLabel={title}
      >
        <Text style={styles.collapsibleTitle}>{title}</Text>
        <View style={styles.collapsibleRight}>
          {!open && summary ? (
            <Text style={styles.collapsibleSummary} numberOfLines={1}>
              {summary}
            </Text>
          ) : null}
          <Text style={styles.collapsibleChevron}>{open ? '▾' : '▸'}</Text>
        </View>
      </Pressable>
      {open && <View style={styles.collapsibleContent}>{children}</View>}
    </View>
  );
}

/**
 * A row button that opens a picker sheet.
 * Pass `value` for single-select display, or `count` for multi-select display.
 * Pass `icon` to show a Phosphor icon alongside the value on the right.
 */
function PickerTrigger({
  label,
  count,
  value,
  icon,
  onPress,
}: {
  label: string;
  count?: number;
  value?: string;
  icon?: string | null;
  onPress: () => void;
}) {
  const displayValue = value !== undefined
    ? value
    : count !== undefined
    ? (count > 0 ? (count === 1 ? '1 selected' : `${count} selected`) : 'None')
    : 'None';
  return (
    <Pressable style={styles.pickerTrigger} onPress={onPress} accessibilityRole="button">
      <Text style={styles.pickerTriggerLabel}>{label}</Text>
      <View style={styles.pickerTriggerRight}>
        {icon ? (
          <PhosphorIcon name={icon} size={18} color={Palette.textSecondary} />
        ) : null}
        <Text style={styles.pickerTriggerValue} numberOfLines={1}>{displayValue}</Text>
        <PhosphorIcon name="caret-right" size={18} color={Palette.textDisabled} />
      </View>
    </Pressable>
  );
}

type ChipItem = { id: string | number; name: string };

/**
 * Horizontal single-select chip row.
 */
function ChipSelector({
  items,
  selectedId,
  onSelect,
  accent,
}: {
  items: ChipItem[];
  selectedId: string | number | null;
  onSelect: (id: string | number | null) => void;
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
            onPress={() => onSelect(selected ? null : item.id)}
          >
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
// CategorySheet
// ---------------------------------------------------------------------------

function CategorySheet({
  visible,
  categories,
  selectedId,
  onSelect,
  onClose,
  accentPrimary,
}: {
  visible: boolean;
  categories: Category[];
  selectedId: number | null;
  onSelect: (id: number | null) => void;
  onClose: () => void;
  accentPrimary: string;
}) {
  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <Pressable style={styles.sheetBackdrop} onPress={onClose} accessibilityRole="button" accessibilityLabel="Close" />
      <View style={styles.sheet}>
        <View style={styles.sheetHandle} />
        <Text style={styles.sheetTitle}>Category</Text>
        {categories.map((cat, i) => {
          const active = cat.id === selectedId;
          return (
            <Pressable
              key={cat.id}
              style={[styles.sheetOption, i < categories.length - 1 && styles.sheetOptionBorder]}
              onPress={() => onSelect(active ? null : cat.id)}
              accessibilityRole="radio"
              accessibilityState={{ selected: active }}
            >
              <View style={styles.sheetOptionLeft}>
                {cat.icon ? (
                  <PhosphorIcon name={cat.icon} size={20} color={active ? accentPrimary : Palette.textSecondary} />
                ) : null}
                <Text style={[styles.sheetOptionText, active && { color: accentPrimary, fontWeight: FontWeight.semibold }]}>
                  {cat.name}
                </Text>
              </View>
              {active && <Text style={[styles.sheetOptionCheck, { color: accentPrimary }]}>✓</Text>}
            </Pressable>
          );
        })}
        <View style={{ height: Spacing[4] }} />
      </View>
    </Modal>
  );
}

// ---------------------------------------------------------------------------
// SubcategorySheet
// ---------------------------------------------------------------------------

function SubcategorySheet({
  visible,
  subcategories,
  selectedId,
  onSelect,
  onClose,
  accentPrimary,
}: {
  visible: boolean;
  subcategories: Subcategory[];
  selectedId: number | null;
  onSelect: (id: number | null) => void;
  onClose: () => void;
  accentPrimary: string;
}) {
  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <Pressable style={styles.sheetBackdrop} onPress={onClose} accessibilityRole="button" accessibilityLabel="Close" />
      <View style={styles.sheet}>
        <View style={styles.sheetHandle} />
        <Text style={styles.sheetTitle}>Subcategory</Text>
        {subcategories.map((sub, i) => {
          const active = sub.id === selectedId;
          return (
            <Pressable
              key={sub.id}
              style={[styles.sheetOption, i < subcategories.length - 1 && styles.sheetOptionBorder]}
              onPress={() => onSelect(active ? null : sub.id)}
              accessibilityRole="radio"
              accessibilityState={{ selected: active }}
            >
              <Text style={[styles.sheetOptionText, active && { color: accentPrimary, fontWeight: FontWeight.semibold }]}>
                {sub.name}
              </Text>
              {active && <Text style={[styles.sheetOptionCheck, { color: accentPrimary }]}>✓</Text>}
            </Pressable>
          );
        })}
        <View style={{ height: Spacing[4] }} />
      </View>
    </Modal>
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
  divider: {
    height: 1,
    backgroundColor: Palette.border,
    marginVertical: Spacing[4],
  },
  field: {
    marginBottom: Spacing[4],
  },
  fieldLabel: {
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
    fontWeight: FontWeight.semibold,
  },
  colorDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    borderWidth: 1,
    borderColor: Palette.dotBorder,
  },

  // Photo
  photoButton: {
    width: '100%',
    aspectRatio: 3 / 4,
    borderRadius: Radius.lg,
    overflow: 'hidden',
    backgroundColor: Palette.surface2,
    borderWidth: 1,
    borderColor: Palette.border,
    maxHeight: 260,
    marginBottom: Spacing[2],
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
    fontSize: FontSize['4xl'],
  },
  photoPlaceholderText: {
    color: Palette.textSecondary,
    fontSize: FontSize.md,
  },
  removePhoto: {
    marginBottom: Spacing[2],
    alignSelf: 'center',
  },
  removePhotoText: {
    color: Palette.error,
    fontSize: FontSize.sm,
  },

  // Collapsible section
  collapsibleSection: {
    borderTopWidth: 1,
    borderTopColor: Palette.border,
    paddingTop: Spacing[1],
    marginBottom: Spacing[1],
  },
  collapsibleHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: Spacing[3],
  },
  collapsibleTitle: {
    fontSize: FontSize.md,
    fontWeight: FontWeight.semibold,
    color: Palette.textPrimary,
  },
  collapsibleRight: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing[2],
    flex: 1,
    justifyContent: 'flex-end',
  },
  collapsibleSummary: {
    fontSize: FontSize.sm,
    color: Palette.textSecondary,
    flexShrink: 1,
  },
  collapsibleChevron: {
    fontSize: FontSize.sm,
    color: Palette.textSecondary,
  },
  collapsibleContent: {
    paddingBottom: Spacing[3],
  },

  // Picker trigger row
  pickerTrigger: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: Palette.surface2,
    borderRadius: Radius.md,
    borderWidth: 1,
    borderColor: Palette.border,
    paddingHorizontal: Spacing[3],
    paddingVertical: Spacing[3],
    marginBottom: Spacing[2],
  },
  pickerTriggerLabel: {
    fontSize: FontSize.md,
    color: Palette.textPrimary,
  },
  pickerTriggerRight: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing[2],
  },
  pickerTriggerValue: {
    fontSize: FontSize.sm,
    color: Palette.textSecondary,
    flexShrink: 1,
    textAlign: 'right',
  },

  // Brand suggestions
  suggestionsRow: {
    marginTop: Spacing[2],
  },
  suggestionChip: {
    paddingHorizontal: Spacing[3],
    paddingVertical: Spacing[1],
    borderRadius: Radius.full,
    borderWidth: 1,
    borderColor: Palette.border,
    backgroundColor: Palette.surface2,
    marginRight: Spacing[2],
  },
  suggestionChipText: {
    color: Palette.textSecondary,
    fontSize: FontSize.xs,
  },

  // Category / subcategory sheet
  sheetBackdrop: {
    flex: 1,
    backgroundColor: Palette.overlay,
  },
  sheet: {
    backgroundColor: Palette.surface1,
    borderTopLeftRadius: Radius.xl,
    borderTopRightRadius: Radius.xl,
    borderWidth: 1,
    borderColor: Palette.border,
    paddingTop: Spacing[2],
    maxHeight: '70%',
  },
  sheetHandle: {
    width: 36,
    height: 4,
    borderRadius: Radius.full,
    backgroundColor: Palette.border,
    alignSelf: 'center',
    marginBottom: Spacing[3],
  },
  sheetTitle: {
    color: Palette.textSecondary,
    fontSize: FontSize.sm,
    fontWeight: FontWeight.semibold,
    textTransform: 'uppercase',
    letterSpacing: 0.6,
    paddingHorizontal: Spacing[4],
    paddingBottom: Spacing[3],
  },
  sheetOption: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: Spacing[4],
    paddingVertical: Spacing[4],
  },
  sheetOptionBorder: {
    borderBottomWidth: 1,
    borderBottomColor: Palette.borderMuted,
  },
  sheetOptionLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing[3],
  },
  sheetOptionText: {
    color: Palette.textPrimary,
    fontSize: FontSize.md,
  },
  sheetOptionCheck: {
    fontSize: FontSize.md,
    fontWeight: FontWeight.bold,
  },

  // Favorite toggle
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

  // Submit
  submitButton: {
    paddingVertical: Spacing[4],
    borderRadius: Radius.md,
    alignItems: 'center',
    marginTop: Spacing[4],
  },
  submitText: {
    fontSize: FontSize.lg,
    fontWeight: FontWeight.semibold,
  },
});
