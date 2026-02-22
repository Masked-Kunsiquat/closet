/**
 * Outfit Detail screen
 *
 * Shows the outfit's items in a scrollable grid.
 * Log-to-date action: pick a date (today or past), choose is_ootd, weather, notes, save.
 */

import { Image } from 'expo-image';
import { useLocalSearchParams, useRouter } from 'expo-router';
import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  FlatList,
  Modal,
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
import { PhosphorIcon } from '@/components/PhosphorIcon';
import { useAccent } from '@/context/AccentContext';
import { useSettings } from '@/context/SettingsContext';
import { toImageUri } from '@/utils/image';
import { getDatabase } from '@/db';
import {
  deleteOutfit,
  getLogsByDate,
  getOutfitWithItems,
  insertOutfitLog,
  setOotd,
} from '@/db/queries';
import { contrastingTextColor } from '@/utils/color';
import { OutfitWithItems, WeatherCondition } from '@/db/types';

/**
 * Get today's date in local time formatted as YYYY-MM-DD.
 *
 * @returns The local date string formatted as `YYYY-MM-DD`.
 */
function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

// Weather chip definitions — defined at module level to avoid re-creation on each render
type WeatherChipDef = { condition: WeatherCondition; emoji: string; label: string };
const WEATHER_CHIPS: WeatherChipDef[] = [
  { condition: 'Sunny',         emoji: '☀️',  label: 'Sunny' },
  { condition: 'Partly Cloudy', emoji: '⛅',  label: 'Partly Cloudy' },
  { condition: 'Cloudy',        emoji: '☁️',  label: 'Cloudy' },
  { condition: 'Rainy',         emoji: '🌧',  label: 'Rainy' },
  { condition: 'Snowy',         emoji: '❄️',  label: 'Snowy' },
  { condition: 'Windy',         emoji: '💨',  label: 'Windy' },
];

/**
 * Renders the Outfit Detail screen that displays an outfit's items in a 3-column grid,
 * provides a delete action, and exposes a modal for logging the outfit (date, OOTD, weather, notes).
 *
 * Shows image thumbnails or category emoji placeholders for items, a floating "Log Outfit"
 * action button, and navigates to item or date-specific log views as appropriate.
 *
 * @returns The React element for the outfit detail screen.
 */
export default function OutfitDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const outfitId = parseInt(id ?? '', 10);
  const { accent } = useAccent();
  const insets = useSafeAreaInsets();
  const router = useRouter();

  // undefined = not yet loaded; null = loaded but not found
  const [outfit, setOutfit] = useState<OutfitWithItems | null | undefined>(undefined);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [logModalVisible, setLogModalVisible] = useState(false);

  const load = useCallback(async () => {
    if (!Number.isFinite(outfitId) || outfitId <= 0) return;
    setLoadError(null);
    try {
      const db = await getDatabase();
      const result = await getOutfitWithItems(db, outfitId);
      setOutfit(result); // null when the DB returns no row
    } catch (e) {
      setLoadError(String(e));
    }
  }, [outfitId]);

  useEffect(() => { load(); }, [load]);

  const [deleting, setDeleting] = useState(false);

  const handleDelete = () => {
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
  };

  if (!Number.isFinite(outfitId) || outfitId <= 0) {
    return (
      <View style={[styles.container, { paddingTop: insets.top }]}>
        <View style={styles.center}>
          <Text style={styles.placeholderText}>Outfit not found.</Text>
        </View>
      </View>
    );
  }

  if (loadError) {
    return (
      <View style={[styles.container, { paddingTop: insets.top }]}>
        <View style={styles.center}>
          <Text style={styles.placeholderText}>Failed to load outfit.</Text>
        </View>
      </View>
    );
  }

  // undefined = not yet loaded; null = loaded but not found
  if (outfit == null) {
    return (
      <View style={[styles.container, { paddingTop: insets.top }]}>
        <View style={styles.center}>
          <Text style={styles.placeholderText}>
            {outfit === undefined ? 'Loading…' : 'Outfit not found.'}
          </Text>
        </View>
      </View>
    );
  }

  return (
    <View style={[styles.container, { paddingTop: insets.top }]}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} hitSlop={10} style={styles.backBtn}>
          <PhosphorIcon name="caret-left" size={18} color={Palette.textSecondary} />
          <Text style={styles.back}>Back</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle} numberOfLines={1}>
          {outfit.name ?? 'Untitled Outfit'}
        </Text>
        <TouchableOpacity onPress={handleDelete} hitSlop={10}>
          <Text style={styles.deleteText}>Delete</Text>
        </TouchableOpacity>
      </View>

      {/* Item strip */}
      <FlatList
        data={outfit.items}
        keyExtractor={(i) => String(i.id)}
        numColumns={3}
        columnWrapperStyle={styles.gridRow}
        contentContainerStyle={[styles.scrollContent, styles.grid]}
        ListHeaderComponent={
          <Text style={styles.sectionTitle}>
            {outfit.items.length} item{outfit.items.length !== 1 ? 's' : ''}
          </Text>
        }
        renderItem={({ item }) => (
          <Pressable
            style={styles.gridCell}
            onPress={() => router.push(`/item/${item.id}`)}
          >
            {item.image_path ? (
              <Image
                source={{ uri: toImageUri(item.image_path)! }}
                style={styles.gridImage}
                contentFit="cover"
              />
            ) : (
              <View style={styles.gridPlaceholder}>
                <Text style={styles.gridEmoji}>{categoryEmoji(item.category_name)}</Text>
              </View>
            )}
            <Text style={styles.gridLabel} numberOfLines={1}>{item.name}</Text>
          </Pressable>
        )}
      />

      {/* Log FAB */}
      <TouchableOpacity
        style={[styles.fab, { backgroundColor: accent.primary, bottom: insets.bottom + Spacing[4] }]}
        onPress={() => setLogModalVisible(true)}
        activeOpacity={0.85}
      >
        <Text style={[styles.fabText, { color: contrastingTextColor(accent.primary) }]}>Log Outfit</Text>
      </TouchableOpacity>

      {/* Log modal */}
      <LogModal
        visible={logModalVisible}
        outfitId={outfitId}
        onClose={() => setLogModalVisible(false)}
        accent={accent.primary}
      />
    </View>
  );
}

// ---------------------------------------------------------------------------
// Log Modal — choose date, OOTD toggle, weather, optional note, save
/**
 * Modal for creating a log entry for a specific outfit.
 *
 * Presents a date input, an optional "Mark as OOTD" toggle, weather condition chips,
 * temperature inputs, notes field, and a save action that inserts a log and optionally
 * sets the OOTD for the selected date.
 *
 * @param visible - Whether the modal is visible.
 * @param outfitId - Database ID of the outfit being logged.
 * @param onClose - Callback invoked to close the modal.
 * @param accent - Accent color used to style interactive controls.
 * @returns The modal UI that handles validation, displays OOTD warnings, prompts to replace an existing OOTD when necessary, inserts the log into the database, and navigates to the saved date's log view on success.
 */

function LogModal({
  visible,
  outfitId,
  onClose,
  accent,
}: {
  visible: boolean;
  outfitId: number;
  onClose: () => void;
  accent: string;
}) {
  const router = useRouter();
  const { settings } = useSettings();

  const [date, setDate] = useState(todayIso());
  const [isOotd, setIsOotd] = useState(false);
  const [weatherCondition, setWeatherCondition] = useState<WeatherCondition | null>(null);
  const [tempLow, setTempLow] = useState('');
  const [tempHigh, setTempHigh] = useState('');
  const [notes, setNotes] = useState('');
  const [saving, setSaving] = useState(false);

  // Reset form state each time the modal opens
  useEffect(() => {
    if (!visible) return;
    setDate(todayIso());
    setIsOotd(false);
    setWeatherCondition(null);
    setTempLow('');
    setTempHigh('');
    setNotes('');
    setSaving(false);
  }, [visible]);

  // When date changes check if OOTD is already taken
  const [ootdTaken, setOotdTaken] = useState(false);
  useEffect(() => {
    if (!visible) return;
    let cancelled = false;
    (async () => {
      try {
        const db = await getDatabase();
        const logs = await getLogsByDate(db, date);
        if (!cancelled) setOotdTaken(logs.some((l) => l.is_ootd === 1));
      } catch {
        // Non-critical — leave ootdTaken as-is; save path re-validates server-side.
      }
    })();
    return () => { cancelled = true; };
  }, [date, visible]);

  const buildWeatherPayload = () => ({
    temperature_low:  tempLow  !== '' ? parseFloat(tempLow)  : null,
    temperature_high: tempHigh !== '' ? parseFloat(tempHigh) : null,
    weather_condition: weatherCondition,
  });

  const handleSave = async () => {
    // Validate YYYY-MM-DD format and semantic correctness (e.g., reject Feb 30)
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) {
      Alert.alert('Invalid date', 'Enter a date in YYYY-MM-DD format.');
      return;
    }
    const [dy, dm, dd] = date.split('-').map(Number);
    const dateObj = new Date(dy, dm - 1, dd);
    if (dateObj.getFullYear() !== dy || dateObj.getMonth() + 1 !== dm || dateObj.getDate() !== dd) {
      Alert.alert('Invalid date', 'Enter a valid calendar date.');
      return;
    }
    setSaving(true);
    try {
      const db = await getDatabase();

      if (isOotd && ootdTaken) {
        // Ask user to confirm replacing existing OOTD
        Alert.alert(
          'Replace OOTD?',
          `${date} already has an OOTD. Set this one instead?`,
          [
            { text: 'Cancel', style: 'cancel', onPress: () => setSaving(false) },
            {
              text: 'Replace', style: 'destructive',
              onPress: async () => {
                let navigateTo: string | null = null;
                try {
                  const logId = await insertOutfitLog(db, {
                    outfit_id: outfitId,
                    date,
                    is_ootd: 0,   // insert neutral first to avoid index conflict
                    notes: notes.trim() || null,
                    ...buildWeatherPayload(),
                  });
                  await setOotd(db, logId, date);
                  navigateTo = date;
                  Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
                } catch (e) {
                  Alert.alert('Error', String(e));
                } finally {
                  setSaving(false);
                  onClose();
                }
                if (navigateTo) router.push({ pathname: '/log/[date]', params: { date: navigateTo } });
              },
            },
          ]
        );
        return;
      }

      const logId = await insertOutfitLog(db, {
        outfit_id: outfitId,
        date,
        is_ootd: isOotd ? 1 : 0,
        notes: notes.trim() || null,
        ...buildWeatherPayload(),
      });

      // If we set OOTD but skipped the replace dialog path
      if (isOotd && !ootdTaken) {
        await setOotd(db, logId, date);
      }

      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      setSaving(false);
      onClose();
      router.push({ pathname: '/log/[date]', params: { date } });
    } catch (e) {
      Alert.alert('Error', String(e));
      setSaving(false);
    }
  };

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <Pressable style={styles.backdrop} onPress={onClose} />
      <View style={styles.sheet}>
        <View style={styles.sheetHandle} />
        <Text style={styles.sheetTitle}>Log Outfit</Text>

        <ScrollView
          showsVerticalScrollIndicator={false}
          keyboardShouldPersistTaps="handled"
          contentContainerStyle={styles.sheetScrollContent}
        >
          {/* Date */}
          <Text style={styles.fieldLabel}>Date (YYYY-MM-DD)</Text>
          <TextInput
            style={styles.fieldInput}
            value={date}
            onChangeText={setDate}
            placeholder="2025-03-21"
            placeholderTextColor={Palette.textDisabled}
            keyboardType="numbers-and-punctuation"
            maxLength={10}
          />

          {/* OOTD toggle */}
          <TouchableOpacity
            style={styles.ootdRow}
            onPress={() => setIsOotd((v) => !v)}
            activeOpacity={0.75}
          >
            <View>
              <Text style={styles.ootdLabel}>Mark as OOTD</Text>
              {ootdTaken && !isOotd && (
                <Text style={styles.ootdWarning}>OOTD already set for this date</Text>
              )}
            </View>
            <View style={[styles.toggle, isOotd && { backgroundColor: accent }]}>
              <View style={[
                styles.toggleKnob,
                isOotd && styles.toggleKnobOn,
                isOotd && { backgroundColor: contrastingTextColor(accent) },
              ]} />
            </View>
          </TouchableOpacity>

          {/* Weather condition chips */}
          <Text style={styles.fieldLabel}>Weather</Text>
          <View style={styles.chipRow}>
            {WEATHER_CHIPS.map(({ condition, emoji, label }) => {
              const isActive = weatherCondition === condition;
              return (
                <TouchableOpacity
                  key={condition}
                  style={[
                    styles.weatherChip,
                    isActive && { backgroundColor: accent, borderColor: accent },
                  ]}
                  onPress={() => setWeatherCondition(isActive ? null : condition)}
                  activeOpacity={0.75}
                >
                  <Text style={styles.weatherChipEmoji}>{emoji}</Text>
                  <Text style={[
                    styles.weatherChipLabel,
                    isActive && { color: contrastingTextColor(accent) },
                  ]}>
                    {label}
                  </Text>
                </TouchableOpacity>
              );
            })}
          </View>

          {/* Temperature */}
          <Text style={styles.fieldLabel}>Temperature</Text>
          <View style={styles.tempRow}>
            <View style={styles.tempField}>
              <TextInput
                style={[styles.fieldInput, styles.tempInput]}
                value={tempLow}
                onChangeText={setTempLow}
                placeholder="Low"
                placeholderTextColor={Palette.textDisabled}
                keyboardType="numbers-and-punctuation"
                maxLength={6}
              />
              <Text style={styles.tempUnit}>°{settings.temperatureUnit}</Text>
            </View>
            <View style={styles.tempField}>
              <TextInput
                style={[styles.fieldInput, styles.tempInput]}
                value={tempHigh}
                onChangeText={setTempHigh}
                placeholder="High"
                placeholderTextColor={Palette.textDisabled}
                keyboardType="numbers-and-punctuation"
                maxLength={6}
              />
              <Text style={styles.tempUnit}>°{settings.temperatureUnit}</Text>
            </View>
          </View>

          {/* Notes */}
          <Text style={styles.fieldLabel}>Notes (optional)</Text>
          <TextInput
            style={[styles.fieldInput, styles.fieldInputMultiline]}
            value={notes}
            onChangeText={setNotes}
            placeholder="Mood, occasion, context…"
            placeholderTextColor={Palette.textDisabled}
            multiline
            numberOfLines={3}
          />

          <TouchableOpacity
            style={[styles.saveButton, { backgroundColor: accent }, saving && { opacity: 0.6 }]}
            onPress={handleSave}
            disabled={saving}
            activeOpacity={0.85}
          >
            <Text style={[styles.saveButtonText, { color: contrastingTextColor(accent) }]}>
              {saving ? 'Saving…' : 'Save Log'}
            </Text>
          </TouchableOpacity>
        </ScrollView>
      </View>
    </Modal>
  );
}

// ---------------------------------------------------------------------------
// Helpers
/**
 * Maps an outfit category name to a representative emoji.
 *
 * @param name - The category name (e.g., "Tops", "Footwear"); may be `null`.
 * @returns An emoji string representing the category, or `🧺` if the category is unknown or `null`.
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
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  placeholderText: {
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
  backBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing[1],
    minWidth: 60,
  },
  back: {
    color: Palette.textSecondary,
    fontSize: FontSize.md,
  },
  headerTitle: {
    color: Palette.textPrimary,
    fontSize: FontSize.md,
    fontWeight: FontWeight.semibold,
    flex: 1,
    textAlign: 'center',
    paddingHorizontal: Spacing[2],
  },
  deleteText: {
    color: Palette.error,
    fontSize: FontSize.md,
    minWidth: 60,
    textAlign: 'right',
  },
  scroll: {
    flex: 1,
  },
  scrollContent: {
    padding: Spacing[4],
    paddingBottom: Spacing[16],
  },
  sectionTitle: {
    color: Palette.textSecondary,
    fontSize: FontSize.sm,
    fontWeight: FontWeight.medium,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: Spacing[3],
  },
  grid: {
    gap: Spacing[2],
  },
  gridRow: {
    gap: Spacing[2],
    marginBottom: Spacing[2],
  },
  gridCell: {
    flex: 1,
    borderRadius: Radius.sm,
    overflow: 'hidden',
    backgroundColor: Palette.surface1,
  },
  gridImage: {
    width: '100%',
    aspectRatio: 3 / 4,
  },
  gridPlaceholder: {
    width: '100%',
    aspectRatio: 3 / 4,
    backgroundColor: Palette.surface2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  gridEmoji: {
    fontSize: 28,
  },
  gridLabel: {
    color: Palette.textPrimary,
    fontSize: FontSize.xs,
    padding: Spacing[1],
    paddingHorizontal: Spacing[2],
  },

  // FAB
  fab: {
    position: 'absolute',
    right: Spacing[5],
    paddingHorizontal: Spacing[5],
    paddingVertical: Spacing[3],
    borderRadius: Radius.full,
    elevation: 6,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.4,
    shadowRadius: 6,
  },
  fabText: {
    fontSize: FontSize.md,
    fontWeight: FontWeight.semibold,
  },

  // Log modal
  backdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.55)',
  },
  sheet: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    maxHeight: '90%',
    backgroundColor: Palette.surface1,
    borderTopLeftRadius: Radius.xl,
    borderTopRightRadius: Radius.xl,
    paddingTop: Spacing[5],
    paddingHorizontal: Spacing[5],
  },
  sheetScrollContent: {
    gap: Spacing[3],
    paddingBottom: Spacing[10],
  },
  sheetHandle: {
    width: 36,
    height: 4,
    backgroundColor: Palette.border,
    borderRadius: Radius.full,
    alignSelf: 'center',
    marginBottom: Spacing[2],
  },
  sheetTitle: {
    color: Palette.textPrimary,
    fontSize: FontSize.lg,
    fontWeight: FontWeight.semibold,
    textAlign: 'center',
    marginBottom: Spacing[2],
  },
  fieldLabel: {
    color: Palette.textSecondary,
    fontSize: FontSize.sm,
    fontWeight: FontWeight.medium,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: Spacing[1],
  },
  fieldInput: {
    backgroundColor: Palette.surface2,
    borderRadius: Radius.md,
    borderWidth: 1,
    borderColor: Palette.border,
    padding: Spacing[3],
    color: Palette.textPrimary,
    fontSize: FontSize.md,
  },
  fieldInputMultiline: {
    height: 72,
    textAlignVertical: 'top',
  },
  ootdRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: Spacing[2],
  },
  ootdLabel: {
    color: Palette.textPrimary,
    fontSize: FontSize.md,
  },
  ootdWarning: {
    color: Palette.warning,
    fontSize: FontSize.xs,
    marginTop: 2,
  },
  toggle: {
    width: 44,
    height: 24,
    borderRadius: 12,
    backgroundColor: Palette.surface3,
    justifyContent: 'center',
    paddingHorizontal: 2,
  },
  toggleKnob: {
    width: 20,
    height: 20,
    borderRadius: 10,
    backgroundColor: Palette.textDisabled,
  },
  toggleKnobOn: {
    alignSelf: 'flex-end',
  },

  // Weather chips
  chipRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing[2],
  },
  weatherChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing[1],
    paddingHorizontal: Spacing[3],
    paddingVertical: Spacing[2],
    borderRadius: Radius.full,
    borderWidth: 1,
    borderColor: Palette.border,
    backgroundColor: Palette.surface3,
  },
  weatherChipEmoji: {
    fontSize: FontSize.md,
  },
  weatherChipLabel: {
    color: Palette.textSecondary,
    fontSize: FontSize.sm,
  },

  // Temperature inputs
  tempRow: {
    flexDirection: 'row',
    gap: Spacing[3],
  },
  tempField: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing[2],
  },
  tempInput: {
    flex: 1,
  },
  tempUnit: {
    color: Palette.textSecondary,
    fontSize: FontSize.md,
  },

  saveButton: {
    paddingVertical: Spacing[3],
    borderRadius: Radius.md,
    alignItems: 'center',
    marginTop: Spacing[2],
  },
  saveButtonText: {
    fontSize: FontSize.md,
    fontWeight: FontWeight.semibold,
  },
});
