/**
 * Outfit Detail screen
 *
 * Shows the outfit's items in a scrollable grid.
 * Log-to-date action: pick a date (today or past), choose is_ootd, save.
 */

import { Image } from 'expo-image';
import { useLocalSearchParams, useRouter } from 'expo-router';
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
import { useAccent } from '@/context/AccentContext';
import { getDatabase } from '@/db';
import {
  clearOotd,
  deleteOutfit,
  getLogsByDate,
  getOutfitWithItems,
  insertOutfitLog,
  setOotd,
} from '@/db/queries';
import { OutfitWithItems } from '@/db/types';

/** Returns today's date as YYYY-MM-DD in local time. */
function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

export default function OutfitDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const outfitId = Number(id);
  const { accent } = useAccent();
  const insets = useSafeAreaInsets();
  const router = useRouter();

  const [outfit, setOutfit] = useState<OutfitWithItems | null>(null);
  const [logModalVisible, setLogModalVisible] = useState(false);

  const load = useCallback(async () => {
    const db = await getDatabase();
    const result = await getOutfitWithItems(db, outfitId);
    setOutfit(result);
  }, [outfitId]);

  useEffect(() => { load(); }, [load]);

  const handleDelete = () => {
    Alert.alert('Delete outfit?', 'This will remove the outfit. Logged history is kept.', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete', style: 'destructive',
        onPress: async () => {
          const db = await getDatabase();
          await deleteOutfit(db, outfitId);
          router.replace('/(tabs)/outfits');
        },
      },
    ]);
  };

  if (!outfit) {
    return (
      <View style={[styles.container, { paddingTop: insets.top }]}>
        <View style={styles.center}>
          <Text style={styles.placeholderText}>Loading…</Text>
        </View>
      </View>
    );
  }

  return (
    <View style={[styles.container, { paddingTop: insets.top }]}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} hitSlop={10}>
          <Text style={styles.back}>← Back</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle} numberOfLines={1}>
          {outfit.name ?? 'Untitled Outfit'}
        </Text>
        <TouchableOpacity onPress={handleDelete} hitSlop={10}>
          <Text style={styles.deleteText}>Delete</Text>
        </TouchableOpacity>
      </View>

      {/* Item strip */}
      <ScrollView style={styles.scroll} contentContainerStyle={styles.scrollContent}>
        <Text style={styles.sectionTitle}>
          {outfit.items.length} item{outfit.items.length !== 1 ? 's' : ''}
        </Text>
        <FlatList
          data={outfit.items}
          keyExtractor={(i) => String(i.id)}
          numColumns={3}
          scrollEnabled={false}
          columnWrapperStyle={styles.gridRow}
          contentContainerStyle={styles.grid}
          renderItem={({ item }) => (
            <Pressable
              style={styles.gridCell}
              onPress={() => router.push(`/item/${item.id}`)}
            >
              {item.image_path ? (
                <Image
                  source={{ uri: item.image_path }}
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
      </ScrollView>

      {/* Log FAB */}
      <TouchableOpacity
        style={[styles.fab, { backgroundColor: accent.primary, bottom: insets.bottom + Spacing[4] }]}
        onPress={() => setLogModalVisible(true)}
        activeOpacity={0.85}
      >
        <Text style={styles.fabText}>Log Outfit</Text>
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
// Log Modal — choose date, OOTD toggle, optional note, save
// ---------------------------------------------------------------------------

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
  const [date, setDate] = useState(todayIso());
  const [isOotd, setIsOotd] = useState(false);
  const [notes, setNotes] = useState('');
  const [saving, setSaving] = useState(false);

  // When date changes check if OOTD is already taken
  const [ootdTaken, setOotdTaken] = useState(false);
  useEffect(() => {
    if (!visible) return;
    getDatabase().then(async (db) => {
      const logs = await getLogsByDate(db, date);
      setOotdTaken(logs.some((l) => l.is_ootd === 1));
    });
  }, [date, visible]);

  const handleSave = async () => {
    // Validate YYYY-MM-DD format
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) {
      Alert.alert('Invalid date', 'Enter a date in YYYY-MM-DD format.');
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
                const logId = await insertOutfitLog(db, {
                  outfit_id: outfitId,
                  date,
                  is_ootd: 0,   // insert neutral first to avoid index conflict
                  notes: notes.trim() || null,
                });
                await setOotd(db, logId, date);
                setSaving(false);
                onClose();
                router.push(`/log/${date}` as any);
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
      });

      // If we set OOTD but skipped the replace dialog path
      if (isOotd && !ootdTaken) {
        await setOotd(db, logId, date);
      }

      setSaving(false);
      onClose();
      router.push(`/log/${date}` as any);
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
            <View style={[styles.toggleKnob, isOotd && styles.toggleKnobOn]} />
          </View>
        </TouchableOpacity>

        {/* Notes */}
        <Text style={styles.fieldLabel}>Notes (optional)</Text>
        <TextInput
          style={[styles.fieldInput, styles.fieldInputMultiline]}
          value={notes}
          onChangeText={setNotes}
          placeholder="Weather, mood, occasion…"
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
          <Text style={styles.saveButtonText}>{saving ? 'Saving…' : 'Save Log'}</Text>
        </TouchableOpacity>
      </View>
    </Modal>
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
  back: {
    color: Palette.textSecondary,
    fontSize: FontSize.md,
    minWidth: 60,
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
    color: '#000',
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
    backgroundColor: Palette.surface1,
    borderTopLeftRadius: Radius.xl,
    borderTopRightRadius: Radius.xl,
    padding: Spacing[5],
    paddingBottom: Spacing[8],
    gap: Spacing[3],
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
    backgroundColor: '#000',
    alignSelf: 'flex-end',
  },
  saveButton: {
    paddingVertical: Spacing[3],
    borderRadius: Radius.md,
    alignItems: 'center',
    marginTop: Spacing[2],
  },
  saveButtonText: {
    color: '#000',
    fontSize: FontSize.md,
    fontWeight: FontWeight.semibold,
  },
});
